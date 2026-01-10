# Ad Price - Real-Time Ad Click Processing System

## Overview

Ad Price is a distributed stream processing application built on Apache Samza that processes ad-click events in real-time. The system consumes ad-click events from Kafka, processes them to calculate revenue distribution, and outputs pricing information for stores/businesses in New York City.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Ad Price System                          │
└─────────────────────────────────────────────────────────────────┘

                    Input Stream                                    
┌────────────────────────────────────────────────────────────────┐
│                     Kafka Topic                                │
│                    "ad-click"                                  │
│  (User ad-click events partitioned by userId)                 │
└──────────────────────┬─────────────────────────────────────────┘
                       │
                       ▼
┌────────────────────────────────────────────────────────────────┐
│              Apache Samza Stream Processor                     │
│                                                                │
│  ┌──────────────────────────────────────────────────┐         │
│  │           AdPriceTask                            │         │
│  │  - Processes ad-click messages                   │         │
│  │  - Partitioned by userId                         │         │
│  │  - Uses Key-Value stores for state               │         │
│  │  - References NYCstoreAds.json for ad prices     │         │
│  └──────────────────────────────────────────────────┘         │
│                                                                │
│  ┌──────────────────────────────────────────────────┐         │
│  │      Static Data: NYCstoreAds.json               │         │
│  │  - Store IDs and names                           │         │
│  │  - Ad prices per store (100-1000 units)          │         │
│  └──────────────────────────────────────────────────┘         │
└──────────────────────┬─────────────────────────────────────────┘
                       │
                       ▼
┌────────────────────────────────────────────────────────────────┐
│                     Kafka Topic                                │
│                    "ad-price"                                  │
│         (Revenue distribution output)                          │
└────────────────────────────────────────────────────────────────┘


Deployment Architecture:
┌────────────────────────────────────────────────────────────────┐
│                         YARN Cluster                           │
│                                                                │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐  │
│  │    Master    │     │   Worker 1   │     │   Worker 2   │  │
│  │    Node      │     │    Node      │     │    Node      │  │
│  ├──────────────┤     ├──────────────┤     ├──────────────┤  │
│  │  ZooKeeper   │     │ Kafka Broker │     │ Kafka Broker │  │
│  │  Kafka Broker│     │ Samza Task   │     │ Samza Task   │  │
│  │  HDFS        │     │              │     │              │  │
│  └──────────────┘     └──────────────┘     └──────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

## Technology Stack

- **Apache Samza 1.2.0**: Stream processing framework
- **Apache Kafka 0.10.1.1**: Message broker for input/output streams
- **Apache Hadoop 2.8.3**: Distributed file system (HDFS) and YARN resource manager
- **Java 8**: Programming language
- **Maven**: Build and dependency management

## Prerequisites

- Java JDK 8 or higher
- Maven 3.0.0 or higher
- Access to a Hadoop/YARN cluster with:
  - HDFS
  - Kafka brokers
  - ZooKeeper
- Network connectivity to cluster nodes

## Project Structure

```
ad-price/
├── pom.xml                           # Maven configuration
├── runner.sh                         # Deployment script
├── src/
│   ├── main/
│   │   ├── assembly/
│   │   │   └── src.xml              # Assembly configuration
│   │   ├── config/
│   │   │   └── ad-price.properties  # Samza job configuration
│   │   ├── java/
│   │   │   └── com/cloudcomputing/samza/nycabs/
│   │   │       ├── AdPriceTask.java                # Main stream processing task
│   │   │       ├── AdPriceConfig.java              # Stream configuration
│   │   │       └── application/
│   │   │           └── AdPriceTaskApplication.java # Application descriptor
│   │   └── resources/
│   │       ├── NYCstoreAds.json     # Static data: store ad prices
│   │       └── log4j.xml            # Logging configuration
│   └── test/
│       └── java/
│           └── com/cloudcomputing/samza/nycabs/
│               ├── TestAdPriceTask.java   # Unit tests
│               └── TestUtils.java         # Test utilities
├── references/                       # Reference file for citations
└── README.md                        # This file
```

## Configuration

### Cluster Configuration

Before running the application, update the following configuration files:

#### 1. `src/main/config/ad-price.properties`

Update the following properties with your cluster details:

```properties
# YARN package path - replace with your master node DNS
yarn.package.path=hdfs://ip-1-2-3-4.ec2.internal:8020/${project.artifactId}-${pom.version}-dist.tar.gz

# ZooKeeper connection - replace with your master node DNS
systems.kafka.consumer.zookeeper.connect=ip-1-2-3-4.ec2.internal:2181/

# Kafka brokers - replace with all cluster nodes DNS
systems.kafka.producer.bootstrap.servers=ip-1-2-3-4.ec2.internal:9092,ip-5-6-7-8.ec2.internal:9092,ip-9-0-1-2.ec2.internal:9092
```

#### 2. `src/main/java/.../application/AdPriceTaskApplication.java`

If needed for local development, update the Kafka connection defaults in the application code. However, for production deployment, the values in `ad-price.properties` take precedence.

```java
// These are default values - override them in ad-price.properties for cluster deployment
private static final List<String> KAFKA_CONSUMER_ZK_CONNECT = ImmutableList.of("localhost:2181");
private static final List<String> KAFKA_PRODUCER_BOOTSTRAP_SERVERS = ImmutableList.of("localhost:9092");
```

## Build Instructions

### Compile and Package

```bash
mvn clean package
```

This will:
1. Compile the Java source code
2. Run unit tests
3. Create a distributable tar.gz file in the `target/` directory: `nycabs-0.0.1-dist.tar.gz`

### Build Artifacts

After successful build, you'll find:
- `target/nycabs-0.0.1.jar` - Application JAR
- `target/nycabs-0.0.1-dist.tar.gz` - Distribution package for deployment

## Deployment

### Using the Runner Script

The `runner.sh` script automates the deployment process:

```bash
./runner.sh
```

This script performs the following steps:
1. Creates deployment directory: `deploy/samza`
2. Builds the project using Maven
3. Extracts the distribution to the deployment folder
4. Copies the distribution to HDFS
5. Launches the Samza job on YARN

### Manual Deployment

1. **Build the project:**
   ```bash
   mvn clean package
   ```

2. **Create deployment directory:**
   ```bash
   mkdir -p deploy/samza
   ```

3. **Extract distribution:**
   ```bash
   tar -xvf target/nycabs-0.0.1-dist.tar.gz -C deploy/samza/
   ```

4. **Upload to HDFS:**
   ```bash
   hadoop fs -copyFromLocal -f target/nycabs-0.0.1-dist.tar.gz /
   ```

5. **Run the job:**
   ```bash
   deploy/samza/bin/run-app.sh \
     --config-factory=org.apache.samza.config.factories.PropertiesConfigFactory \
     --config-path="file://$PWD/deploy/samza/config/ad-price.properties"
   ```

## Testing

### Run Unit Tests

```bash
mvn test
```

### Test Structure

The project includes test utilities in:
- `TestAdPriceTask.java`: Unit tests for the AdPriceTask
- `TestUtils.java`: Helper utilities for testing

To add test cases, implement them in `TestAdPriceTask.java`:

```java
@Test
public void testAdPriceTask() throws Exception {
    // Implement your test cases
}
```

## Data Format

### Input Stream: ad-click

The application expects ad-click events on the `ad-click` Kafka topic. Messages should be JSON formatted and partitioned by `userId`.

### Output Stream: ad-price

The application produces revenue distribution data to the `ad-price` Kafka topic.

### Static Data: NYCstoreAds.json

Contains store information and ad pricing in newline-delimited JSON (NDJSON) format, with one JSON object per line:

```json
{"storeId": "H4jJ7XB3CetIr1pg56CczQ", "name": "Levain Bakery", "adPrice": 700}
{"storeId": "xEnNFXtMLDF5kZDxfaCJgA", "name": "The Halal Guys", "adPrice": 400}
{"storeId": "44SY464xDHbvOcjDzRbKkQ", "name": "Ippudo NY", "adPrice": 1000}
```

Ad prices range from 100 to 1000 units per store.

## Key Features

- **Stream Partitioning**: Messages are partitioned by `userId`, ensuring all events for a user are processed by the same task
- **Stateful Processing**: Uses Samza's Key-Value stores for maintaining state across messages
- **Fault Tolerance**: Leverages YARN for resource management and fault tolerance
- **Scalability**: Can scale horizontally by adding more Samza tasks
- **Offset Management**: Configured to read from the oldest offset for deterministic processing

## Development Notes

### Adding Key-Value Stores

To add additional state stores, update `ad-price.properties`:

```properties
# Add your KV store configuration
stores.<store-name>.factory=...
stores.<store-name>.changelog=...
```

### Implementing Stream Processing Logic

The main processing logic should be implemented in `AdPriceTask.java`:

```java
@Override
public void process(IncomingMessageEnvelope envelope, 
                    MessageCollector collector, 
                    TaskCoordinator coordinator) {
    // Your processing logic here
}
```

## Monitoring and Logs

Logging is configured through `log4j.xml`. Logs can be monitored through:
- YARN application logs
- Samza container logs
- Kafka consumer/producer logs

## Contributing

When contributing to this project:
1. Follow the existing code structure
2. Add unit tests for new functionality
3. Update this README if adding new features
4. Document any external references in the `references` file

## License

This project is part of a cloud computing course assignment.

## References

See the `references` file for proper citations of external sources used in this project.
