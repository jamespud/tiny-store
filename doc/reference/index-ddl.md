# 读模型/索引 DDL（示意）

```sql
CREATE TABLE IF NOT EXISTS projection_offset (
  projection_name varchar(128) NOT NULL,
  topic varchar(128) NOT NULL,
  partition int NOT NULL,
  offset bigint NOT NULL,
  PRIMARY KEY(projection_name, topic, partition)
);
```
