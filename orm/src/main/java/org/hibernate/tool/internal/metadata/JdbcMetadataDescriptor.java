package org.hibernate.tool.internal.metadata;

import java.util.Properties;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.internal.BootstrapContextImpl;
import org.hibernate.boot.internal.InFlightMetadataCollectorImpl;
import org.hibernate.boot.internal.MetadataBuilderImpl.MetadataBuildingOptionsImpl;
import org.hibernate.boot.internal.MetadataBuildingContextRootImpl;
import org.hibernate.boot.internal.MetadataImpl;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.boot.spi.MetadataBuildingOptions;
import org.hibernate.cfg.Environment;
import org.hibernate.tool.api.metadata.MetadataDescriptor;
import org.hibernate.tool.api.reveng.ReverseEngineeringStrategy;
import org.hibernate.tool.internal.reveng.DefaultReverseEngineeringStrategy;
import org.hibernate.tool.internal.reveng.JdbcBinder;

public class JdbcMetadataDescriptor implements MetadataDescriptor {
	
	private ReverseEngineeringStrategy reverseEngineeringStrategy = new DefaultReverseEngineeringStrategy();
    private Properties properties = new Properties();

	public JdbcMetadataDescriptor(
			ReverseEngineeringStrategy reverseEngineeringStrategy, 
			Properties properties) {
		this.properties.putAll(Environment.getProperties());
		if (properties != null) {
			this.properties.putAll(properties);
		}
		if (reverseEngineeringStrategy != null) {
			this.reverseEngineeringStrategy = reverseEngineeringStrategy;
		}
		if (this.properties.get(MetadataDescriptor.PREFER_BASIC_COMPOSITE_IDS) == null) {
			this.properties.put(MetadataDescriptor.PREFER_BASIC_COMPOSITE_IDS, true);
		}
	}

	public Properties getProperties() {
		Properties result = new Properties();
		result.putAll(properties);
		return result;
	}
    
	public Metadata createMetadata() {
		StandardServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
				.applySettings(getProperties())
				.build();
		MetadataBuildingOptionsImpl metadataBuildingOptions = 
				new MetadataBuildingOptionsImpl( serviceRegistry );	
		BootstrapContextImpl bootstrapContext = new BootstrapContextImpl(
				serviceRegistry, 
				metadataBuildingOptions);
		metadataBuildingOptions.setBootstrapContext(bootstrapContext);
		InFlightMetadataCollectorImpl metadataCollector = 
				getMetadataCollector(
						bootstrapContext, 
						metadataBuildingOptions);
		MetadataBuildingContext metadataBuildingContext = 
				new MetadataBuildingContextRootImpl(
						bootstrapContext,
						metadataBuildingOptions, 
						metadataCollector);
		MetadataImpl metadata = metadataCollector
				.buildMetadataInstance(metadataBuildingContext);
		metadata.getTypeConfiguration().scope(metadataBuildingContext);
		JdbcBinder binder = new JdbcBinder(
				serviceRegistry, 
				getProperties(), 
				metadataBuildingContext, 
				reverseEngineeringStrategy, 
				(Boolean)this.properties.get(MetadataDescriptor.PREFER_BASIC_COMPOSITE_IDS));
		return binder.readFromDatabase(metadata);
	}
	
	private InFlightMetadataCollectorImpl getMetadataCollector(
		BootstrapContext bootstrapContext,
		MetadataBuildingOptions metadataBuildingOptions) {
		return new InFlightMetadataCollectorImpl(
			bootstrapContext,
			metadataBuildingOptions);	
	}
	
}
