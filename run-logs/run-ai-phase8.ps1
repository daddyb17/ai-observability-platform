cd "C:\Users\HP\Documents\aiobservabilityplatform"
$env:POSTGRES_URL='jdbc:postgresql://localhost:55432/aiobs'
$env:POSTGRES_USER='aiobs'
$env:POSTGRES_PASSWORD='aiobs'
$env:KAFKA_BOOTSTRAP_SERVERS='localhost:9092'
$env:AI_PROVIDER='mock'
.\mvnw.cmd -pl services/ai-analysis-service spring-boot:run
