import { For, Show, createMemo, createSignal, onMount } from "solid-js";
import type { JdbcColumn, JdbcQueryResult } from "@dev.hiconic/tf.extension.jdbc.support_jdbc-console-api-model";
import { JdbcConsoleApi, type RuntimeConfig } from "./api";

type Mode = "query" | "write";
type View = "table" | "text";

export function App() {
	let runtimeConfig: RuntimeConfig | undefined;
  const [api, setApi] = createSignal<JdbcConsoleApi>();
  const [databases, setDatabases] = createSignal<string[]>([]);
  const [database, setDatabase] = createSignal("");
  const [statement, setStatement] = createSignal("select 1");
  const [rowLimit, setRowLimit] = createSignal(500);
  const [canWrite, setCanWrite] = createSignal(false);
  const [mode, setMode] = createSignal<Mode>("query");
  const [view, setView] = createSignal<View>("table");
  const [result, setResult] = createSignal<JdbcQueryResult>();
  const [status, setStatus] = createSignal("");
  const [error, setError] = createSignal("");
  const [running, setRunning] = createSignal(false);
  const textResult = createMemo(() => result() ? renderText(result()!) : "");

  onMount(async () => {
    try {
      const config = await loadConfig();
      runtimeConfig = config;
      const client = new JdbcConsoleApi(config); setApi(client);
      const capabilities = await client.capabilities();
      setCanWrite(capabilities.canWrite);
      const reflected = await client.databases();
      const names = modeledArray<{ name: string }>(reflected.databases).map(item => item.name);
      setDatabases(names); setDatabase(names[0] ?? "");
    } catch (caught) { handleError(caught); }
  });

  async function execute() {
    const client = api();
    if (!client || !database() || !statement().trim()) return;
    setRunning(true); setError(""); setStatus(""); setResult(undefined);
    try {
      if (mode() === "query") {
        const response = await client.query(database(), statement(), rowLimit());
        setResult(response);
        setStatus(`${modeledArray(response.rows).length} row(s) · ${response.durationInMs} ms${response.truncated ? " · truncated" : ""}`);
      } else {
        const response = await client.statement(database(), statement());
        setStatus(`${response.updateCount} row(s) affected · ${response.durationInMs} ms`);
      }
    } catch (caught) { handleError(caught); }
    finally { setRunning(false); }
  }

  function handleError(caught: unknown) {
    const message = caught instanceof Error ? caught.message : String(caught);
		if (runtimeConfig && isAuthenticationFailure(message))
			redirectToLogin(runtimeConfig);
    setError(message);
  }

  return <main>
    <header><div><span class="eyebrow">Database tools</span><h1>JDBC Console</h1></div><span class="state">{running() ? "Running…" : "Ready"}</span></header>
    <section class="controls">
      <label>Database<select value={database()} onInput={event => setDatabase(event.currentTarget.value)}><For each={databases()}>{name => <option value={name}>{name}</option>}</For></select></label>
      <div class="mode"><button classList={{active: mode() === "query"}} onClick={() => setMode("query")}>Query</button><Show when={canWrite()}><button classList={{active: mode() === "write"}} onClick={() => setMode("write")}>Write</button></Show></div>
      <Show when={mode() === "query"}><label class="limit">Limit<input type="number" min="1" value={rowLimit()} onInput={event => setRowLimit(Number(event.currentTarget.value))}/></label></Show>
    </section>
    <section class="editor"><textarea spellcheck={false} value={statement()} onInput={event => setStatement(event.currentTarget.value)} onKeyDown={event => { if ((event.ctrlKey || event.metaKey) && event.key === "Enter") execute(); }}/><button class="execute" disabled={running() || !database()} onClick={execute}>Execute <kbd>Ctrl ↵</kbd></button></section>
    <Show when={error()}><div class="error">{error()}</div></Show>
    <Show when={status()}><div class="status">{status()}</div></Show>
    <Show when={result()}>{queryResult => <section class="results">
      <div class="resultbar"><div class="mode"><button classList={{active: view() === "table"}} onClick={() => setView("table")}>Table</button><button classList={{active: view() === "text"}} onClick={() => setView("text")}>Text</button></div><button class="copy" onClick={() => navigator.clipboard.writeText(textResult())}>Copy</button></div>
      <Show when={view() === "table"} fallback={<pre class="text-result">{textResult()}</pre>}>
        <div class="table-wrap"><table><thead><tr><For each={modeledArray<JdbcColumn>(queryResult().columns)}>{column => <th title={`${column.databaseTypeName} (${column.jdbcType})`}>{column.label || column.name}</th>}</For></tr></thead>
        <tbody><For each={modeledArray<{ values: unknown }>(queryResult().rows)}>{row => <tr><For each={modeledArray<unknown>(row.values)}>{value => <td classList={{null: value == null}}>{display(value)}</td>}</For></tr>}</For></tbody></table></div>
      </Show>
      <Show when={modeledArray<string>(queryResult().warnings).length}><div class="warnings"><For each={modeledArray<string>(queryResult().warnings)}>{warning => <div>{warning}</div>}</For></div></Show>
    </section>}</Show>
  </main>;
}

async function loadConfig(): Promise<RuntimeConfig> {
  const response = await fetch("./runtime-config.json", { cache: "no-store" });
  if (!response.ok) throw new Error(`Runtime configuration unavailable (${response.status})`);
  return response.json();
}

function redirectToLogin(config: RuntimeConfig) {
  const loginUrl = new URL(`${config.servicesUrl.replace(/\/$/, "")}/login`, window.location.origin);
  loginUrl.searchParams.set("continue", `${window.location.pathname}${window.location.search}${window.location.hash}`);
  window.location.replace(loginUrl);
}

function isAuthenticationFailure(message: string) {
  return /MissingSession|MissingCredentials|AuthenticationFailure|SessionNotFound|No authenticated web session|Unauthorized|401/i.test(message);
}

function display(value: unknown): string {
  if (value == null) return "NULL";
  if (value instanceof Date) return value.toISOString();
  return String(value);
}

function renderText(result: JdbcQueryResult): string {
  const columns = modeledArray<JdbcColumn>(result.columns);
  const rows = modeledArray<{ values: unknown }>(result.rows).map(row => modeledArray<unknown>(row.values).map(display));
  const widths = columns.map((column, index) => Math.min(60, Math.max((column.label || column.name || "").length, ...rows.map(row => (row[index] ?? "").length))));
  const line = widths.map(width => "-".repeat(width + 2)).join("+");
  const renderRow = (values: string[]) => values.map((value, index) => ` ${clip(value, widths[index]).padEnd(widths[index])} `).join("|");
  return [renderRow(columns.map(column => column.label || column.name || "")), line, ...rows.map(renderRow)].join("\n");
}

function clip(value: string, width: number) {
  const oneLine = value.replaceAll("\r", "\\r").replaceAll("\n", "\\n").replaceAll("\t", "\\t");
  return oneLine.length <= width ? oneLine : `${oneLine.slice(0, Math.max(0, width - 1))}…`;
}

function modeledArray<T>(value: unknown): T[] {
  if (value == null) return [];
  return Array.from(value as Iterable<T>);
}
