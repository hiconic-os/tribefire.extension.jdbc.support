import { For, Show, createMemo, createSignal, onCleanup, onMount } from "solid-js";
import type { JdbcColumn, JdbcQueryResult } from "@dev.hiconic/tf.extension.jdbc.support_jdbc-console-api-model";
import { JdbcConsoleApi, type RuntimeConfig } from "./api";

type Mode = "query" | "write";
type View = "table" | "text";
type DownloadFormat = "csv" | "tsv" | "json" | "txt";
type AccessState = "checking" | "authenticated" | "unauthenticated" | "denied" | "offline" | "error";

export function App() {
	let runtimeConfig: RuntimeConfig | undefined;
  const [api, setApi] = createSignal<JdbcConsoleApi>();
  const [databases, setDatabases] = createSignal<string[]>([]);
  const [database, setDatabase] = createSignal("");
  const [statement, setStatement] = createSignal("select 1");
  const [rowLimit, setRowLimit] = createSignal(500);
  const [canWrite, setCanWrite] = createSignal(false);
  const [canRead, setCanRead] = createSignal(false);
  const [accessState, setAccessState] = createSignal<AccessState>("checking");
  const [accessDetail, setAccessDetail] = createSignal("");
  const [mode, setMode] = createSignal<Mode>("query");
  const [view, setView] = createSignal<View>("table");
  const [downloadFormat, setDownloadFormat] = createSignal<DownloadFormat>("csv");
  const [queryPaneHeight, setQueryPaneHeight] = createSignal(224);
  const [result, setResult] = createSignal<JdbcQueryResult>();
  const [status, setStatus] = createSignal("");
  const [error, setError] = createSignal("");
  const [running, setRunning] = createSignal(false);
  const textResult = createMemo(() => result() ? renderText(result()!) : "");

  onMount(() => void initialize());

  async function initialize() {
    setAccessState("checking");
    try {
      const config = await loadConfig();
      runtimeConfig = config;
      const client = new JdbcConsoleApi(config); setApi(client);
      await refreshAccess(true);
    } catch (caught) { handleError(caught); }
  }

  function retryAccess() {
    if (api()) void refreshAccess(databases().length === 0);
    else void initialize();
  }

  const recheckAfterReturn = () => {
    if (document.visibilityState === "visible") void refreshAccess(false);
  };
  onMount(() => {
    window.addEventListener("focus", recheckAfterReturn);
    document.addEventListener("visibilitychange", recheckAfterReturn);
  });
  onCleanup(() => {
    window.removeEventListener("focus", recheckAfterReturn);
    document.removeEventListener("visibilitychange", recheckAfterReturn);
  });

  async function refreshAccess(loadDatabases: boolean) {
    const client = api();
    if (!client) return;
    setAccessState("checking");
    setAccessDetail("");
    try {
      const capabilities = await client.capabilities();
      setCanRead(capabilities.canRead);
      setCanWrite(capabilities.canWrite);
      if (!capabilities.authenticated) {
        setAccessState("unauthenticated");
        return;
      }
      if (!capabilities.canRead) {
        setAccessState("denied");
        return;
      }
      setAccessState("authenticated");
      setError("");
      if (loadDatabases || databases().length === 0) {
        const reflected = await client.databases();
        const names = modeledArray<{ name: string }>(reflected.databases).map(item => item.name);
        setDatabases(names);
        if (!names.includes(database())) setDatabase(names[0] ?? "");
      }
    } catch (caught) {
      applyAccessFailure(caught);
    }
  }

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
    if (runtimeConfig) {
      if (isAuthenticationFailure(message) || isConnectivityFailure(message) || isAuthorizationFailure(message))
        applyAccessFailure(caught);
      else
        setApi(new JdbcConsoleApi(runtimeConfig));
    }
    setError(message);
  }

  function applyAccessFailure(caught: unknown) {
    const message = caught instanceof Error ? caught.message : String(caught);
    setAccessDetail(message);
    setCanRead(false);
    setCanWrite(false);
    if (isConnectivityFailure(message)) setAccessState("offline");
    else if (isAuthorizationFailure(message)) setAccessState("denied");
    else if (isAuthenticationFailure(message)) setAccessState("unauthenticated");
    else setAccessState("error");
  }

  function beginResize(event: PointerEvent) {
    event.preventDefault();
    const startY = event.clientY;
    const startHeight = queryPaneHeight();
    const move = (moveEvent: PointerEvent) => {
      const maximum = Math.max(180, window.innerHeight - 210);
      setQueryPaneHeight(Math.min(maximum, Math.max(150, startHeight + moveEvent.clientY - startY)));
    };
    const finish = () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", finish);
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", finish);
  }

  function downloadResult() {
    const queryResult = result();
    if (!queryResult) return;
    const format = downloadFormat();
    const content = serializeResult(queryResult, format);
    const mimeType = format === "json" ? "application/json" : format === "csv" ? "text/csv" : "text/plain";
    const url = URL.createObjectURL(new Blob([content], { type: `${mimeType};charset=utf-8` }));
    const link = document.createElement("a");
    link.href = url;
    link.download = `${fileNamePart(database() || "query")}-${fileTimestamp()}.${format}`;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url));
  }

  return <><main classList={{masked: accessState() !== "authenticated"}}>
    <section class="query-pane" style={`height:${queryPaneHeight()}px`}>
      <div class="controls">
        <div class="branding"><span class="eyebrow">Database tools</span><strong>JDBC Console</strong></div>
        <label>Database<select value={database()} onInput={event => setDatabase(event.currentTarget.value)}><For each={databases()}>{name => <option value={name}>{name}</option>}</For></select></label>
        <div class="mode"><button classList={{active: mode() === "query"}} onClick={() => setMode("query")}>Query</button><Show when={canWrite()}><button classList={{active: mode() === "write"}} onClick={() => setMode("write")}>Write</button></Show></div>
        <Show when={mode() === "query"}><label class="limit">Limit<input type="number" min="1" value={rowLimit()} onInput={event => setRowLimit(Number(event.currentTarget.value))}/></label></Show>
        <span class="state">{running() ? "Running…" : canRead() ? (canWrite() ? "Read · Write" : "Read only") : "Checking access"}</span>
      </div>
      <div class="editor"><textarea aria-label="SQL statement" spellcheck={false} value={statement()} onInput={event => setStatement(event.currentTarget.value)} onKeyDown={event => { if ((event.ctrlKey || event.metaKey) && event.key === "Enter") execute(); }}/><button class="execute" disabled={running() || !database()} onClick={execute}>Execute <kbd>Ctrl ↵</kbd></button></div>
    </section>
    <div class="splitter" role="separator" aria-orientation="horizontal" aria-label="Resize statement and result areas" onPointerDown={beginResize}><span/></div>
    <section class="results">
      <div class="resultbar">
        <div class="result-summary">
          <Show when={result()}><div class="mode"><button classList={{active: view() === "table"}} onClick={() => setView("table")}>Table</button><button classList={{active: view() === "text"}} onClick={() => setView("text")}>Text</button></div></Show>
          <Show when={status()}><span class="status">{status()}</span></Show>
        </div>
        <Show when={result()}><div class="result-actions"><button class="secondary" onClick={() => navigator.clipboard.writeText(textResult())}>Copy</button><select aria-label="Download format" value={downloadFormat()} onInput={event => setDownloadFormat(event.currentTarget.value as DownloadFormat)}><option value="csv">CSV</option><option value="tsv">TSV</option><option value="json">JSON</option><option value="txt">Formatted text</option></select><button class="secondary" onClick={downloadResult}>Download</button></div></Show>
      </div>
      <Show when={error()}><div class="error">{error()}</div></Show>
      <Show when={result()} fallback={<div class="empty-result">Execute a query to inspect its result.</div>}>{queryResult => <>
        <div class="result-content">
          <Show when={view() === "table"} fallback={<pre class="text-result">{textResult()}</pre>}>
            <div class="table-wrap"><table><thead><tr><For each={modeledArray<JdbcColumn>(queryResult().columns)}>{column => <th title={`${column.databaseTypeName} (${column.jdbcType})`}>{column.label || column.name}</th>}</For></tr></thead>
            <tbody><For each={modeledArray<{ values: unknown }>(queryResult().rows)}>{row => <tr><For each={modeledArray<unknown>(row.values)}>{value => <td classList={{null: value == null}}>{display(value)}</td>}</For></tr>}</For></tbody></table></div>
          </Show>
        </div>
        <Show when={modeledArray<string>(queryResult().warnings).length}><div class="warnings"><For each={modeledArray<string>(queryResult().warnings)}>{warning => <div>{warning}</div>}</For></div></Show>
      </>}</Show>
    </section>
  </main><Show when={accessState() !== "authenticated"}><AccessOverlay state={accessState()} detail={accessDetail()}
      loginUrl={runtimeConfig ? loginUrl(runtimeConfig) : undefined} retry={retryAccess}/></Show></>;
}

async function loadConfig(): Promise<RuntimeConfig> {
  const response = await fetch("./runtime-config.json", { cache: "no-store" });
  if (!response.ok) throw new Error(`Runtime configuration unavailable (${response.status})`);
  return response.json();
}

function loginUrl(config: RuntimeConfig) {
  const url = new URL(config.loginUrl ?? `${config.servicesUrl.replace(/\/$/, "")}/login`, window.location.origin);
  url.searchParams.set("continue", `${window.location.pathname}${window.location.search}${window.location.hash}`);
  return url.toString();
}

function isAuthenticationFailure(message: string) {
  return /MissingSession|MissingCredentials|AuthenticationFailure|SessionNotFound|No authenticated web session|Unauthorized|401/i.test(message);
}

function isAuthorizationFailure(message: string) {
  return /Forbidden|Insufficient.*role|required role|403/i.test(message);
}

function isConnectivityFailure(message: string) {
  return /Failed to fetch|NetworkError|Network request failed|Load failed|timeout|HTTP 50[234]/i.test(message);
}

function AccessOverlay(props: { state: AccessState; detail: string; loginUrl?: string; retry: () => void }) {
  const title = () => props.state === "checking" ? "Checking access…"
    : props.state === "offline" ? "Connection interrupted"
    : props.state === "denied" ? "Access denied"
    : props.state === "error" ? "Access check failed"
    : "Session required";
  const explanation = () => props.state === "offline" ? "The server cannot currently be reached. Your work remains in this browser."
    : props.state === "denied" ? "Your session is valid, but it has no JDBC Console reader role."
    : props.state === "error" ? "The server returned an unexpected error while checking access."
    : props.state === "unauthenticated" ? "Sign in to use the JDBC Console. Your current input remains available."
    : "Verifying session and capabilities.";
  return <div class="access-overlay" role="dialog" aria-live="polite"><div class="access-card"><strong>{title()}</strong><p>{explanation()}</p>
    <Show when={props.detail && props.state !== "checking"}><small>{props.detail}</small></Show>
    <div class="access-actions"><Show when={props.state === "offline" || props.state === "error"}><button onClick={props.retry}>Try again</button></Show>
      <Show when={props.state === "unauthenticated" && props.loginUrl}><a class="login" href={props.loginUrl}>Sign in</a></Show></div>
  </div></div>;
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

function serializeResult(result: JdbcQueryResult, format: DownloadFormat): string {
  if (format === "txt") return renderText(result);
  const columns = modeledArray<JdbcColumn>(result.columns);
  const rows = modeledArray<{ values: unknown }>(result.rows).map(row => modeledArray<unknown>(row.values));
  if (format === "json") return JSON.stringify({
    columns: columns.map(column => ({
      name: column.name,
      label: column.label,
      valueKind: column.valueKind,
      jdbcType: column.jdbcType,
      databaseTypeName: column.databaseTypeName
    })),
    rows: rows.map(row => row.map(jsonValue)),
    truncated: result.truncated,
    durationInMs: result.durationInMs,
    warnings: modeledArray<string>(result.warnings)
  }, null, 2);
  const separator = format === "csv" ? "," : "\t";
  const encode = (value: unknown) => delimitedValue(value, separator);
  return [columns.map(column => encode(column.label || column.name)), ...rows.map(row => row.map(encode))]
    .map(row => row.join(separator)).join("\r\n");
}

function delimitedValue(value: unknown, separator: string): string {
  if (value == null) return "";
  const rendered = display(value);
  if (separator === "," || rendered.includes(separator) || /["\r\n]/.test(rendered))
    return `"${rendered.replaceAll('"', '""')}"`;
  return rendered;
}

function jsonValue(value: unknown): unknown {
  if (value == null || typeof value === "string" || typeof value === "number" || typeof value === "boolean") return value;
  if (value instanceof Date) return value.toISOString();
  if (typeof value === "bigint") return value.toString();
  return String(value);
}

function fileNamePart(value: string) {
  return value.replaceAll(/[^a-zA-Z0-9._-]+/g, "-").replaceAll(/^-+|-+$/g, "") || "query";
}

function fileTimestamp() {
  return new Date().toISOString().replaceAll(/[:.]/g, "-");
}

function clip(value: string, width: number) {
  const oneLine = value.replaceAll("\r", "\\r").replaceAll("\n", "\\n").replaceAll("\t", "\\t");
  return oneLine.length <= width ? oneLine : `${oneLine.slice(0, Math.max(0, width - 1))}…`;
}

function modeledArray<T>(value: unknown): T[] {
  if (value == null) return [];
  return Array.from(value as Iterable<T>);
}
