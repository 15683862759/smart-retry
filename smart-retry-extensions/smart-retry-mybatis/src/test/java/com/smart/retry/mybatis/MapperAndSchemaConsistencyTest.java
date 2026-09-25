package com.smart.retry.mybatis;

import org.junit.Assert;
import org.junit.Test;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.smart.retry.mybatis.dao.RetryTaskDao;

import javax.xml.parsers.DocumentBuilderFactory;
import java.lang.reflect.Method;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class MapperAndSchemaConsistencyTest {

    @Test
    public void testRetryTaskDaoStatementsExistInEveryDatabaseDialect() throws Exception {
        Set<String> required = new HashSet<>();
        for (Method method : RetryTaskDao.class.getMethods()) {
            required.add(method.getName());
        }
        Assert.assertTrue(statementIds("postgresql").containsAll(required));
        Assert.assertTrue(statementIds("oracle").containsAll(required));
    }

    @Test
    public void testUniqueKeyIndexesAreUniqueInEverySchema() throws IOException {
        String mysql = read("../../doc/smart_retry_mysql.sql");
        String postgres = read("../../doc/smart_retry_pg.sql");
        String oracle = read("../../doc/smart_retry_oracle.sql");

        Pattern mysqlUniqueIndex = Pattern.compile(
                "UNIQUE\\s+KEY\\s+`uk_unique_key`\\s*\\(`unique_key`,\\s*`active_flag`\\)",
                Pattern.CASE_INSENSITIVE);
        Pattern postgresUniqueIndex = Pattern.compile(
                "CREATE\\s+UNIQUE\\s+INDEX\\s+IF\\s+NOT\\s+EXISTS\\s+uk_unique_key\\s+ON\\s+retry_task\\s*\\(unique_key,\\s*active_flag\\)",
                Pattern.CASE_INSENSITIVE);
        Pattern oracleUniqueIndex = Pattern.compile(
                "CREATE\\s+UNIQUE\\s+INDEX\\s+uk_unique_key\\s+ON\\s+retry_task\\s*\\(unique_key,\\s*active_flag\\)",
                Pattern.CASE_INSENSITIVE);

        Assert.assertTrue("MySQL unique_key should only deduplicate active tasks",
                mysqlUniqueIndex.matcher(mysql).find());
        Assert.assertTrue("PostgreSQL unique_key should use a unique index",
                postgresUniqueIndex.matcher(postgres).find());
        Assert.assertTrue("Oracle unique_key should use a unique index",
                oracleUniqueIndex.matcher(oracle).find());
    }

    @Test
    public void testActiveFlagIsMaintainedByTriggerInEverySchema() throws IOException {
        String mysql = read("../../doc/smart_retry_mysql.sql");
        String postgres = read("../../doc/smart_retry_pg.sql");
        String oracle = read("../../doc/smart_retry_oracle.sql");

        Assert.assertTrue("MySQL should maintain active_flag on insert and update",
                mysql.contains("trg_retry_task_active_flag_insert")
                        && mysql.contains("trg_retry_task_active_flag_update"));
        Assert.assertTrue("PostgreSQL should maintain active_flag on insert and update",
                postgres.contains("BEFORE INSERT OR UPDATE ON retry_task"));
        Assert.assertTrue("Oracle should maintain active_flag on insert and update",
                oracle.contains("BEFORE INSERT OR UPDATE ON retry_task"));
    }

    @Test
    public void testRetryNumGuardsAreConsistentInEveryDialect() throws Exception {
        String[] dialects = {"mysql", "postgresql", "oracle"};
        for (String dialect : dialects) {
            Document document = parse(Paths.get(
                    "src/main/resources", dialect, "retry-task-mapper.xml"));
            Element claim = statement(document, "claimTask");
            Element markNull = statement(document, "markNullTaskObjectFail");

            Assert.assertTrue(dialect + " claimTask should only claim tasks with retry_num >= 1",
                    text(claim).contains("retry_num >= 1"));
            Assert.assertTrue(dialect + " markNullTaskObjectFail should allow retry_num >= 0",
                    text(markNull).contains("retry_num >= 0"));
        }
    }

    @Test
    public void testOracleScrambleDeadShardingUsesOracleUpdateSyntax() throws Exception {
        Document document = parse(Paths.get(
                "src/main/resources/oracle/retry-sharding-mapper.xml"));
        Element scramble = statement(document, "scrambleDeadSharding");
        String sql = text(scramble);

        Assert.assertFalse("Oracle UPDATE SET clauses must not qualify columns with a table alias",
                Pattern.compile("SET\\s+rs\\.", Pattern.CASE_INSENSITIVE).matcher(sql).find());
        Assert.assertTrue("Oracle UPDATE should target retry_sharding without a table alias",
                Pattern.compile("UPDATE\\s+retry_sharding\\s+SET",
                        Pattern.CASE_INSENSITIVE).matcher(sql).find());
    }

    @Test
    public void testScrambleDeadShardingRechecksHeartbeatBeforeUpdate() throws Exception {
        Pattern mysqlGuard = Pattern.compile(
                "WHERE\\s+rs\\.id\\s*=\\s*tmp\\.id\\s+AND\\s+rs\\.last_heartbeat\\s*<",
                Pattern.CASE_INSENSITIVE);
        Pattern postgresGuard = Pattern.compile(
                "WHERE\\s+retry_sharding\\.id\\s*=\\s*expired\\.id\\s+AND\\s+retry_sharding\\.last_heartbeat\\s*<",
                Pattern.CASE_INSENSITIVE);
        Pattern oracleGuard = Pattern.compile(
                "\\)\\s+AND\\s+last_heartbeat\\s*<",
                Pattern.CASE_INSENSITIVE);

        String[][] cases = {
                {"mysql", "src/main/resources/mysql/retry-sharding-mapper.xml"},
                {"postgresql", "src/main/resources/postgresql/retry-sharding-mapper.xml"},
                {"oracle", "src/main/resources/oracle/retry-sharding-mapper.xml"}
        };
        for (String[] dialectCase : cases) {
            String dialect = dialectCase[0];
            Document document = parse(Paths.get(dialectCase[1]));
            Element scramble = statement(document, "scrambleDeadSharding");
            String sql = text(scramble);
            Pattern guard = "mysql".equals(dialect) ? mysqlGuard
                    : "postgresql".equals(dialect) ? postgresGuard : oracleGuard;

            Assert.assertTrue(dialect + " scrambleDeadSharding must recheck heartbeat timeout in the outer UPDATE",
                    guard.matcher(sql).find());
        }
    }

    private Set<String> statementIds(String dialect) throws Exception {
        Document document = parse(Paths.get(
                "src/main/resources", dialect, "retry-task-mapper.xml"));
        Set<String> ids = new HashSet<>();
        NodeList statements = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < statements.getLength(); i++) {
            if (statements.item(i) instanceof Element) {
                Element statement = (Element) statements.item(i);
                String tag = statement.getTagName();
                if ("select".equals(tag) || "insert".equals(tag)
                        || "update".equals(tag) || "delete".equals(tag)) {
                    ids.add(statement.getAttribute("id"));
                }
            }
        }
        return ids;
    }

    private Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private Element statement(Document document, String id) {
        NodeList statements = document.getElementsByTagName("update");
        for (int i = 0; i < statements.getLength(); i++) {
            if (statements.item(i) instanceof Element) {
                Element element = (Element) statements.item(i);
                if (id.equals(element.getAttribute("id"))) {
                    return element;
                }
            }
        }
        throw new AssertionError("Missing update statement: " + id);
    }

    private String text(Element element) {
        return element.getTextContent();
    }

    private String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
