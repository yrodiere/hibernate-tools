package org.hibernate.tool.internal.reveng.reader;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.mapping.ForeignKey;
import org.hibernate.mapping.Table;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.api.dialect.MetaDataDialect;
import org.hibernate.tool.api.reveng.ReverseEngineeringStrategy;
import org.hibernate.tool.api.reveng.SchemaSelection;
import org.hibernate.tool.internal.reveng.DatabaseCollector;
import org.hibernate.tool.internal.reveng.ForeignKeyProcessor;
import org.hibernate.tool.internal.reveng.ForeignKeysInfo;
import org.hibernate.tool.internal.reveng.IndexProcessor;
import org.hibernate.tool.internal.reveng.PrimaryKeyProcessor;

public class DatabaseReader {

	public static DatabaseReader create(Properties properties, ReverseEngineeringStrategy revengStrategy,
			MetaDataDialect mdd, ServiceRegistry serviceRegistry) {
		ConnectionProvider connectionProvider = serviceRegistry.getService(ConnectionProvider.class);
		String defaultCatalogName = properties.getProperty(AvailableSettings.DEFAULT_CATALOG);
		String defaultSchemaName = properties.getProperty(AvailableSettings.DEFAULT_SCHEMA);
		return new DatabaseReader(mdd, connectionProvider, defaultCatalogName, defaultSchemaName, revengStrategy);
	}

	private final ReverseEngineeringStrategy revengStrategy;

	private MetaDataDialect metadataDialect;

	private final ConnectionProvider provider;

	private final String defaultSchema;
	private final String defaultCatalog;

	private DatabaseReader(MetaDataDialect dialect, ConnectionProvider provider, String defaultCatalog,
			String defaultSchema, ReverseEngineeringStrategy reveng) {
		this.metadataDialect = dialect;
		this.provider = provider;
		this.revengStrategy = reveng;
		this.defaultCatalog = defaultCatalog;
		this.defaultSchema = defaultSchema;
		if (revengStrategy == null) {
			throw new IllegalStateException("Strategy cannot be null");
		}
	}

	public List<Table> readDatabaseSchema(DatabaseCollector dbs, String catalog, String schema) {
		try {
			getMetaDataDialect().configure(provider);

			Set<Table> hasIndices = new HashSet<Table>();

			List<Table> foundTables = new ArrayList<Table>();
			for (Iterator<SchemaSelection> iter = getSchemaSelections(catalog, schema).iterator(); iter.hasNext();) {
				SchemaSelection selection = iter.next();
				foundTables.addAll(
						TableCollector.create(getMetaDataDialect(), revengStrategy, dbs, selection, hasIndices).processTables());
			}

			Iterator<Table> tables = foundTables.iterator(); // not dbs.iterateTables() to avoid "double-read" of
																// columns etc.
			while (tables.hasNext()) {
				Table table = tables.next();
				BasicColumnProcessor.processBasicColumns(getMetaDataDialect(), revengStrategy, defaultSchema,
						defaultCatalog, table);
				PrimaryKeyProcessor.processPrimaryKey(getMetaDataDialect(), revengStrategy, defaultSchema,
						defaultCatalog, dbs, table);
				if (hasIndices.contains(table)) {
					IndexProcessor.processIndices(getMetaDataDialect(), defaultSchema, defaultCatalog, table);
				}
			}

			tables = foundTables.iterator(); // dbs.iterateTables();
			Map<String, List<ForeignKey>> oneToManyCandidates = resolveForeignKeys(dbs, tables);

			dbs.setOneToManyCandidates(oneToManyCandidates);

			return foundTables;
		} finally {
			getMetaDataDialect().close();
			revengStrategy.close();
		}
	}

	/**
	 * Iterates the tables and find all the foreignkeys that refers to something
	 * that is available inside the DatabaseCollector.
	 * 
	 * @param dbs
	 * @param progress
	 * @param tables
	 * @return
	 */
	private Map<String, List<ForeignKey>> resolveForeignKeys(DatabaseCollector dbs, Iterator<Table> tables) {
		List<ForeignKeysInfo> fks = new ArrayList<ForeignKeysInfo>();
		while (tables.hasNext()) {
			Table table = (Table) tables.next();
			// Done here after the basic process of collections as we might not have touched
			// all referenced tables (this ensure the columns are the same instances
			// througout the basic JDBC derived model.
			// after this stage it should be "ok" to divert from keeping columns in sync as
			// it can be required if the same
			// column is used with different aliases in the ORM mapping.
			ForeignKeysInfo foreignKeys = ForeignKeyProcessor.processForeignKeys(getMetaDataDialect(), revengStrategy,
					defaultSchema, defaultCatalog, dbs, table);
			fks.add(foreignKeys);
		}

		Map<String, List<ForeignKey>> oneToManyCandidates = new HashMap<String, List<ForeignKey>>();
		for (Iterator<ForeignKeysInfo> iter = fks.iterator(); iter.hasNext();) {
			ForeignKeysInfo element = iter.next();
			Map<String, List<ForeignKey>> map = element.process(revengStrategy); // the actual foreignkey is created
																					// here.
			mergeMultiMap(oneToManyCandidates, map);
		}
		return oneToManyCandidates;
	}

	public MetaDataDialect getMetaDataDialect() {
		return metadataDialect;
	}

	private void mergeMultiMap(Map<String, List<ForeignKey>> dest, Map<String, List<ForeignKey>> src) {
		Iterator<Entry<String, List<ForeignKey>>> items = src.entrySet().iterator();

		while (items.hasNext()) {
			Entry<String, List<ForeignKey>> element = items.next();

			List<ForeignKey> existing = dest.get(element.getKey());
			if (existing == null) {
				dest.put(element.getKey(), element.getValue());
			} else {
				existing.addAll(element.getValue());
			}
		}

	}

	public Set<String> readSequences(String sql) {
		Set<String> sequences = new HashSet<String>();
		if (sql != null) {
			Connection connection = null;
			try {

				connection = provider.getConnection();
				Statement statement = null;
				ResultSet rs = null;
				try {
					statement = connection.createStatement();
					rs = statement.executeQuery(sql);
					while (rs.next()) {
						sequences.add(rs.getString("SEQUENCE_NAME").toLowerCase().trim());
					}
				} finally {
					if (rs != null)
						rs.close();
					if (statement != null)
						statement.close();
				}

			} catch (SQLException e) {
				throw new RuntimeException("Problem while closing connection", e);
			} finally {
				if (connection != null)
					try {
						provider.closeConnection(connection);
					} catch (SQLException e) {
						throw new RuntimeException("Problem while closing connection", e);
					}
			}
		}
		return sequences;
	}

	private List<SchemaSelection> getSchemaSelections(String catalog, String schema) {
		List<SchemaSelection> result = revengStrategy.getSchemaSelections();
		if (result == null) {
			result = new ArrayList<SchemaSelection>();
			result.add(new SchemaSelection(catalog, schema));
		}
		return result;
	}

}
