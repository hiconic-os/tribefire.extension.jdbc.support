# tribefire.extension.jdbc.support

## RX JDBC Console

The RX JDBC Console is an independent successor to the classic JDBC support UI. Add
`jdbc-console-web-rx-module` to an RX application to install its service, web application and
navigation entry. Configured databases are discovered through the RX database module; no
connector list is maintained separately.

Access is controlled by `JdbcConsoleConfiguration`:

- `readerRoles` defaults to `jdbc-console-reader` and `jdbc-console-writer`.
- `writerRoles` defaults to `jdbc-console-writer`.
- A writer is always treated as a reader, even when custom role sets omit that relation.
- Empty role sets deliberately grant no access.

Read and write operations are separate service requests. Write requests use an explicit
transaction and roll back on failure. JDBC read-only mode is also requested for queries, but
not every driver or database enforces that hint. Database credentials with read-only privileges
remain the hard security boundary for databases that must not be modified by readers.

Query results preserve column order and duplicate labels by representing every row as a
positional `ListRecord`. Common JDBC scalar types remain modeled values; database-specific
values are rendered as strings. Row count, query time and cell length are bounded by the
configuration.
