# INSTRUCTION.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Data Couchbase sample application called "try-cb-spring" that demonstrates Couchbase Server features with Spring Boot. It provides a flight planner and hotel search web application using:

- Couchbase Server as the database
- Spring Boot with Spring Data Couchbase for the backend
- Vue.js and Bootstrap for the frontend (in a separate repository)
- Swagger/OpenAPI for API documentation

The application showcases N1QL (SQL for Documents), Sub-document requests, and Full Text Search (FTS) capabilities.

## Building and Running

### Prerequisites
- Java 8 or later (Java 11 recommended)
- Maven 3 or later
- Docker (for running with Docker)

### Build Commands

```bash
# Install dependencies
mvn clean install

# Run the application
mvn spring-boot:run

# Run with custom database connection parameters
mvn spring-boot:run -Dspring-boot.run.arguments="--storage.host=localhost storage.username=Administrator storage.password=password"
```

### Running with Docker

```bash
# Run the full application (backend, frontend, and Couchbase)
docker-compose up

# Run with your own Couchbase instance
CB_HOST=10.144.211.101 CB_USER=Administrator CB_PSWD=password docker-compose -f mix-and-match.yml up backend frontend

# Run only the database component
docker-compose -f mix-and-match.yml up db
```

### Setting up Couchbase

When running manually, you need to create a full-text search index:

```bash
# Set up environment variables
export CB_HOST=localhost CB_USER=Administrator CB_PSWD=password

# Use the provided script to wait for Couchbase and create necessary indexes
./wait-for-couchbase.sh echo "Couchbase is ready!"
```

## Architecture

The application follows a typical Spring Boot architecture:

- `src/main/java/trycb/Application.java` - Main application entry point
- `src/main/java/trycb/config/` - Configuration classes for Couchbase and other components
- `src/main/java/trycb/model/` - Model classes
- `src/main/java/trycb/service/` - Service layer implementing business logic
- `src/main/java/trycb/web/` - REST controllers
- `src/main/resources/application.properties` - Application configuration

Key components:
1. Database connection configured in `Database.java` and `application.properties`
2. JWT-based authentication for user management
3. REST API endpoints documented with Swagger/OpenAPI
4. Docker integration for easy deployment

## API Documentation

API documentation is available at `http://localhost:8080/apidocs` when the application is running.
