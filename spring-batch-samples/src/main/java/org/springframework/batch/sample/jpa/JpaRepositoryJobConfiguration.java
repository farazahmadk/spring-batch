/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.batch.sample.jpa;

import java.math.BigDecimal;
import java.util.Map;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.RepositoryItemWriter;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.batch.item.data.builder.RepositoryItemWriterBuilder;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.sample.domain.trade.CustomerCredit;
import org.springframework.batch.sample.domain.trade.internal.CustomerCreditIncreaseProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.DefaultPersistenceUnitManager;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitManager;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/**
 * Hibernate JPA dialect does not support custom tx isolation levels => overwrite with
 * ISOLATION_DEFAULT.
 *
 * @author Mahmoud Ben Hassine
 */
@Configuration
@EnableBatchProcessing(isolationLevelForCreate = "ISOLATION_DEFAULT")
@EnableJpaRepositories(basePackages = "org.springframework.batch.sample.jpa")
public class JpaRepositoryJobConfiguration {

	@Bean
	@StepScope
	public RepositoryItemReader<CustomerCredit> itemReader(@Value("#{jobParameters['credit']}") Double credit,
			CustomerCreditPagingAndSortingRepository repository) {
		return new RepositoryItemReaderBuilder<CustomerCredit>().name("itemReader")
			.pageSize(2)
			.methodName("findByCreditGreaterThan")
			.repository(repository)
			.arguments(BigDecimal.valueOf(credit))
			.sorts(Map.of("id", Sort.Direction.ASC))
			.build();
	}

	@Bean
	public RepositoryItemWriter<CustomerCredit> itemWriter(CustomerCreditCrudRepository repository) {
		return new RepositoryItemWriterBuilder<CustomerCredit>().repository(repository).methodName("save").build();
	}

	@Bean
	public Job job(JobRepository jobRepository, JpaTransactionManager transactionManager,
			RepositoryItemReader<CustomerCredit> itemReader, RepositoryItemWriter<CustomerCredit> itemWriter) {
		return new JobBuilder("ioSampleJob", jobRepository)
			.start(new StepBuilder("step1", jobRepository).<CustomerCredit, CustomerCredit>chunk(2, transactionManager)
				.reader(itemReader)
				.processor(new CustomerCreditIncreaseProcessor())
				.writer(itemWriter)
				.build())
			.build();
	}

	// Infrastructure beans

	@Bean
	public DataSource dataSource() {
		return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.HSQL)
			.addScript("/org/springframework/batch/core/schema-drop-hsqldb.sql")
			.addScript("/org/springframework/batch/core/schema-hsqldb.sql")
			.addScript("/org/springframework/batch/sample/jpa/sql/schema.sql")
			.build();
	}

	@Bean
	public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
		return new JpaTransactionManager(entityManagerFactory);
	}

	@Bean
	public EntityManagerFactory entityManagerFactory(PersistenceUnitManager persistenceUnitManager,
			DataSource dataSource) {
		LocalContainerEntityManagerFactoryBean factoryBean = new LocalContainerEntityManagerFactoryBean();
		factoryBean.setDataSource(dataSource);
		factoryBean.setPersistenceUnitManager(persistenceUnitManager);
		factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
		factoryBean.afterPropertiesSet();
		return factoryBean.getObject();
	}

	@Bean
	public PersistenceUnitManager persistenceUnitManager(DataSource dataSource) {
		DefaultPersistenceUnitManager persistenceUnitManager = new DefaultPersistenceUnitManager();
		persistenceUnitManager.setDefaultDataSource(dataSource);
		persistenceUnitManager.afterPropertiesSet();
		return persistenceUnitManager;
	}

}
