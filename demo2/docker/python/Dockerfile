# Lightweight Python image
FROM python:3.11-slim

# Set working directory
WORKDIR /app

# Install common libraries (add more if needed)
RUN pip install --no-cache-dir numpy pandas requests

# Security: Run as non-root user
RUN useradd -m -u 1000 coderunner
USER coderunner

# Default command (will be overridden)
CMD ["python"]