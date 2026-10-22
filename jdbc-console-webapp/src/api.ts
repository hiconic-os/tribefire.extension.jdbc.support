import {
  ExecuteJdbcQuery, ExecuteJdbcStatement, GetJdbcConsoleCapabilities, ListJdbcDatabases,
  type JdbcConsoleCapabilities, type JdbcDatabases, type JdbcQueryResult, type JdbcStatementResult
} from "@dev.hiconic/tf.extension.jdbc.support_jdbc-console-api-model";
import { remote } from "@dev.hiconic/tf.js_hc-js-api";

export interface RuntimeConfig { servicesUrl: string; }

export class JdbcConsoleApi {
  private readonly evaluator;
  constructor(readonly config: RuntimeConfig) {
    const url = new URL(config.servicesUrl, window.location.origin).toString().replace(/\/$/, "");
    this.evaluator = remote.connect(url).evaluatorBuilder().setDefaultDomain("jdbc-console").build();
  }
  capabilities() { return this.evaluate<JdbcConsoleCapabilities>(GetJdbcConsoleCapabilities.create()); }
  databases() { return this.evaluate<JdbcDatabases>(ListJdbcDatabases.create()); }
  query(databaseName: string, statement: string, rowLimit: number) {
    const request = ExecuteJdbcQuery.create();
    request.databaseName = databaseName; request.statement = statement; request.rowLimit = rowLimit;
    return this.evaluate<JdbcQueryResult>(request);
  }
  statement(databaseName: string, statement: string) {
    const request = ExecuteJdbcStatement.create();
    request.databaseName = databaseName; request.statement = statement;
    return this.evaluate<JdbcStatementResult>(request);
  }
  private async evaluate<T>(request: { EvalAndGetReasoned(evaluator: unknown): Promise<any> }): Promise<T> {
    const result = await request.EvalAndGetReasoned(this.evaluator);
    if (result.isUnsatisfied()) {
      const reason = result.whyUnsatisfied();
      throw new Error(reason?.Stringify?.() ?? reason?.asString?.() ?? String(reason));
    }
    return result.get() as T;
  }
}
