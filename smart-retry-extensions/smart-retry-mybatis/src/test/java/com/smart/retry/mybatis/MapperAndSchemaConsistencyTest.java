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
        String postgres = read("../../doc/smart_retry_pg.sql");
        String oracle = read("../../doc/smart_retry_oracle.sql");

        Pattern postgresUniqueIndex = Pattern.compile(
                "CREATE\\s+UNIQUE\\s+INDEX\\s+IF\\s+NOT\\s+EXISTS\\s+uk_unique_key\\s+ON\\s+retry_task\\s*\\(unique_key\\)",
                Pattern.CASE_INSENSITIVE);
        Pattern oracleUniqueIndex = Pattern.compile(
                "CREATE\\s+UNIQUE\\s+INDEX\\s+uk_unique_key\\s+ON\\s+retry_task\\s*\\(unique_key\\)",
                Pattern.CASE_INSENSITIVE);

        Assert.assertTrue("PostgreSQL unique_key should use a unique index",
                postgresUniqueIndex.matcher(postgres).find());
        Assert.assertTrue("Oracle unique_key should use a unique index",
                oracleUniqueIndex.matcher(oracle).find());
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

    private String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
