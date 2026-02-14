FROM node:20-alpine

WORKDIR /tday

COPY package*.json ./
COPY prisma ./prisma
RUN npm install

COPY . .

# Allow CI to pass a dummy DATABASE_URL for prisma generate
ARG DATABASE_URL=postgresql://dummy:dummy@localhost:5432/dummy

# Build assets without running DB migrations during image build.
RUN npx prisma generate && npx next build

EXPOSE 3000

# Run migrations when the container starts (DB is available at runtime).
CMD ["sh", "-c", "npx prisma migrate deploy && npm run start"]
