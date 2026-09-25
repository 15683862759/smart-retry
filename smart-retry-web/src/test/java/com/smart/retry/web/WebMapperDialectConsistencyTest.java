package com.smart.retry.web;

import com.smart.retry.web.dao.WebRetryShardingDao;
import com.smart.retry.web.dao.WebRetryTaskDao;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Web Mapper 数据库方言一致性测试。
 *
 * <p>管理后台与 Starter 共用同一个 SqlSessionFactory，加载路径会随数据库类型切换。
 * 该测试保证 Web DAO 在 MySQL、PostgreSQL、Oracle 三种部署形态下都有可用 SQL，
 * 避免切换数据库后管理接口抛出 Invalid bound statement。</p>
 */
public class WebMapperDialectConsistencyTest {

    /**
     * 校验任务 DAO 的所有接口方法在每种数据库方言中都有对应 SQL statement。
     */
    @Test
    public void testWebRetryTaskDaoStatementsExistInEveryDialect() throws Exception {
        Set<String> required = methodNames(WebRetryTaskDao.class);
        Assert.assertFalse(required.isEmpty());

        for (String dialect : new String[]{"mysql", "postgresql", "oracle"}) {
            Assert.assertEquals("Web task mapper is missing: " + dialect,
                    required, statementIds(mapperPath(dialect, "RetryTaskMapper.xml")));
        }
    }

    /**
     * 校验分片 DAO 的所有接口方法在每种数据库方言中都有对应 SQL statement。
     */
    @Test
    public void testWebRetryShardingDaoStatementsExistInEveryDialect() throws Exception {
        Set<String> required = methodNames(WebRetryShardingDao.class);
        Assert.assertFalse(required.isEmpty());

        for (String dialect : new String[]{"mysql", "postgresql", "oracle"}) {
            Assert.assertEquals("Web sharding mapper is missing: " + dialect,
                    required, statementIds(mapperPath(dialect, "RetryShardingMapper.xml")));
        }
    }

    private Set<String> methodNames(Class<?> daoClass) {
        Set<String> names = new HashSet<>();
        for (Method method : daoClass.getMethods()) {
            if (!method.isDefault()) {
                names.add(method.getName());
            }
        }
        return names;
    }

    private Path mapperPath(String dialect, String fileName) {
        return Paths.get("src/main/resources/mapper", dialect, fileName);
    }

    private Set<String> statementIds(Path path) throws Exception {
        Assert.assertTrue("Mapper XML does not exist: " + path, java.nio.file.Files.exists(path));

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Document document = factory.newDocumentBuilder().parse(path.toFile());

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
}
