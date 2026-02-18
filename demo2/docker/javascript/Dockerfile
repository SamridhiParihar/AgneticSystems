# Lightweight Node.js image
FROM node:20-slim

# Set working directory
WORKDIR /app

# Install common packages
RUN npm install -g lodash axios

# Security: Run as non-root user
RUN useradd -m -u 1000 coderunner
USER coderunner

# Default command
CMD ["node"]