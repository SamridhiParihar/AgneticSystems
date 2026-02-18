# Lightweight Java runtime image
FROM eclipse-temurin:21-jdk-jammy

# Set working directory
WORKDIR /app

# Security: create non-root user
RUN useradd -m -u 1000 coderunner
USER coderunner

# Default command (can be overridden)
CMD ["java"]
