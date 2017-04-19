package org.hibernate.cfg;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.ServiceRegistry;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JDBCMetaDataConfigurationTest {

	@Before
	public void setUp() throws Exception {
		executeDDL(getCreateSQL());
	}

	@Test
	public void testReadFromJDBC() throws Exception {
		JDBCMetaDataConfiguration cfg = new JDBCMetaDataConfiguration();
		cfg.readFromJDBC();
		Metadata metadata = cfg.getMetadata();
		Assert.assertNotNull("Withrealtimestamp", metadata.getEntityBinding("Withrealtimestamp"));
		Assert.assertNotNull("Noversion", metadata.getEntityBinding("Noversion"));
		Assert.assertNotNull("Withfaketimestamp", metadata.getEntityBinding("Withfaketimestamp"));
		Assert.assertNotNull("Withversion", metadata.getEntityBinding("Withversion"));
	}

	private void executeDDL(String[] sqls) throws SQLException {
		Configuration configuration = new Configuration();
		StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder();
		builder.applySettings(configuration.getProperties());
		ServiceRegistry serviceRegistry = builder.build();
		ConnectionProvider connectionProvider = serviceRegistry.getService(ConnectionProvider.class);
		Statement statement = null;
		Connection con = null;
		con = connectionProvider.getConnection();

		statement = con.createStatement();

		for (int i = 0; i < sqls.length; i++) {
			String ddlsql = sqls[i];
			statement.execute(ddlsql);
			con.commit();
		}

		if (statement != null)
			statement.close();
		connectionProvider.closeConnection(con);

	}

	private String[] getCreateSQL() {
		return new String[] {
				"create table withVersion (first int, second int, version int, name varchar(256), primary key (first))",
				"create table noVersion (first int, second int, name varchar(256), primary key (second))",
				"create table withRealTimestamp (first int, second int, timestamp timestamp, name varchar(256), primary key (first))",
				"create table withFakeTimestamp (first int, second int, timestamp int, name varchar(256), primary key (first))", };
	}

}
