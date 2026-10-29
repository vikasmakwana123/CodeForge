FROM node:22-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install --global npm@12.2.1 && npm install
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173"]
