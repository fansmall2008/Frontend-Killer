# Contributing

Welcome to Web GameList Oper! We welcome contributions from everyone.

## How to Contribute

### 1. Fork the Repository

Click the "Fork" button on the GitHub repository page:
[https://github.com/fansmall2008/Frontend-Killer](https://github.com/fansmall2008/Frontend-Killer)

### 2. Clone Your Fork

```bash
git clone https://github.com/your-username/Frontend-Killer.git
cd Frontend-Killer
```

### 3. Create a Branch

```bash
git checkout -b feature/your-feature-name
```

### 4. Make Changes

Make your changes to the codebase. Please follow the existing code style.

### 5. Test Your Changes

```bash
# Run Maven tests
mvn test

# Build the project
mvn clean package -DskipTests

# Run the application
java -jar target/webGamelistOper-1.0.4-beta.jar
```

### 6. Commit Your Changes

```bash
git add .
git commit -m "Description of your changes"
```

### 7. Push to Your Fork

```bash
git push origin feature/your-feature-name
```

### 8. Create a Pull Request

Go to the original repository and click "New Pull Request".

## Code Guidelines

### Java Code Style

- Use 4 spaces for indentation
- Follow Java naming conventions
- Add Javadoc comments for public methods
- Use meaningful variable names

### JavaScript/HTML Code Style

- Use 2 spaces for indentation
- Follow camelCase naming convention
- Add comments for complex logic
- Use `const` and `let` appropriately

### Commit Message Guidelines

- Use imperative mood: "Add feature" not "Added feature"
- Keep messages concise
- Reference issues when applicable: "Fix #123"

## Reporting Issues

### Bug Reports

When reporting bugs, include:
- Steps to reproduce
- Expected behavior
- Actual behavior
- Screenshots (if applicable)
- Log files

### Feature Requests

When requesting features, include:
- Description of the feature
- Use case
- Expected behavior

## Development Setup

### Prerequisites

- Java 17+
- Maven 3.8+
- Git

### Local Development

```bash
# Clone the repository
git clone https://github.com/fansmall2008/Frontend-Killer.git
cd Frontend-Killer

# Build the project
mvn clean package -DskipTests

# Run the application
java -jar target/webGamelistOper-1.0.4-beta.jar

# Access the application
open http://localhost:8081
```

### Docker Development

```bash
# Build the Docker image
docker build -t webgamelistoper:dev .

# Run the container
docker run -p 8081:8080 -v $(pwd)/data:/data webgamelistoper:dev
```

## Project Structure

```
webGamelistOper/
├── src/
│   └── main/
│       ├── java/com/gamelist/
│       │   ├── controller/     # REST controllers
│       │   ├── service/        # Business logic
│       │   ├── repository/     # Data access
│       │   ├── model/          # Entity classes
│       │   ├── util/           # Utility classes
│       │   └── xml/            # XML parsing
│       └── resources/
│           ├── static/         # Frontend files
│           └── application.properties
├── distribution/               # Distribution files
├── wiki/                       # Wiki documentation
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

## Testing

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=GameServiceTest

# Run tests with coverage
mvn jacoco:report
```

### Writing Tests

- Add unit tests for new features
- Follow existing test patterns
- Use JUnit 5 and Mockito
- Aim for high test coverage

## Documentation

### Updating Documentation

- Update README.md for major changes
- Update Wiki pages as needed
- Add Javadoc comments
- Keep API documentation up to date

## Code Review Process

1. Pull Request submitted
2. Automated tests run
3. Reviewer assigned
4. Code review and feedback
5. Changes made if needed
6. Pull Request merged

## Community

- GitHub Issues: [Issue Tracker](https://github.com/fansmall2008/Frontend-Killer/issues)
- Discussions: [GitHub Discussions](https://github.com/fansmall2008/Frontend-Killer/discussions)

## License

By contributing to Web GameList Oper, you agree that your contributions will be licensed under the MIT License.