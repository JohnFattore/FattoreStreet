-- Runs on first-time init of the dev postgres volume only. Django uses the
-- default "postgres" database; Spring Boot expects its own (Flyway migrates
-- the schema on app start).
CREATE DATABASE springboot;
