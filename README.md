# E-Commerce Resilience Platform

## Design Patterns : Circuit Breaker & Bulkhead

**Architecture Logicielle | ING A2 Groupe 1 | Ganed Chaima**

---

## Architecture

```
Client
  └──► Nginx :80  [Round-Robin Load Balancer]
         ├──► OrderService     :8081  [Circuit Breaker → PaymentMock :9090]
         ├──► OrderService     :8083  [instance 2]
         ├──► InventoryService :8082  [Bulkhead readStock + reserveStock]
         └──► InventoryService :8084  [instance 2]
```

## Services

| Service           | Port(s)    | Pattern         | Description                |
| ----------------- | ---------- | --------------- | -------------------------- |
| order-service     | 8081, 8083 | Circuit Breaker | Commandes + appel paiement |
| inventory-service | 8082, 8084 | Bulkhead (x2)   | Gestion stock              |
| payment-mock      | 9090       | —               | API paiement simulée       |
| Nginx             | 8088       | Round-Robin     | Reverse Proxy + LB         |

---

## Démarrage rapide

### Prérequis

- Java 17+
- Maven 3.8+
- Nginx optionnel sur Windows : une version embarquee est fournie dans `nginx/nginx-1.24.0`

### Windows

```bat
cd ecommerce-resilience
scripts\start.bat
```

### Linux / macOS

```bash
cd ecommerce-resilience
chmod +x scripts/start.sh scripts/test.sh
bash scripts/start.sh
```

### Démarrage manuel (par service)

```bash
# Build
cd order-service     && mvn clean package -DskipTests && cd ..
cd inventory-service && mvn clean package -DskipTests && cd ..
cd payment-mock      && mvn clean package -DskipTests && cd ..

# Instance 1 OrderService
java -jar order-service/target/order-service-1.0.0.jar --server.port=8081

# Instance 2 OrderService
java -jar order-service/target/order-service-1.0.0.jar --server.port=8083

# Instance 1 InventoryService
java -jar inventory-service/target/inventory-service-1.0.0.jar --server.port=8082

# Instance 2 InventoryService
java -jar inventory-service/target/inventory-service-1.0.0.jar --server.port=8084

# PaymentMock
java -jar payment-mock/target/payment-mock-1.0.0.jar --server.port=9090
```

### Nginx

```bash
# Linux
sudo cp nginx/nginx.conf /etc/nginx/nginx.conf
sudo nginx -t && sudo nginx

# Windows
# scripts\start.bat demarre automatiquement nginx\nginx-1.24.0\nginx.exe
# avec la configuration du projet : nginx\nginx.conf
```

---

## Tests

```powershell
# Windows
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\test.ps1

# Linux/macOS
bash scripts/test.sh
```

### Tests manuels

```bash
# Ping et Load Balancer
curl http://localhost:8088/api/orders/ping
curl http://localhost:8088/api/inventory/ping

# Créer une commande (passe par Circuit Breaker)
curl -X POST http://localhost:8088/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-001","items":[{"productId":"SKU-LAPTOP","productName":"Laptop","quantity":1,"unitPrice":2499.99}]}'

# État Circuit Breaker
curl http://localhost:8081/api/orders/circuit-status

# Stock (Bulkhead read)
curl http://localhost:8088/api/inventory/SKU-LAPTOP

# Réservation (Bulkhead write)
curl -X POST http://localhost:8088/api/inventory/reserve \
  -H "Content-Type: application/json" \
  -d '{"productId":"SKU-LAPTOP","quantity":1,"orderId":"ORD-001"}'

# Forcer des échecs sur PaymentMock
curl -X PUT "http://localhost:9090/api/payments/config?failRate=70&latencyMs=200"

# Remettre à zéro
curl -X PUT "http://localhost:9090/api/payments/config?failRate=0&latencyMs=50"

# État Bulkhead
curl http://localhost:8088/api/inventory/bulkhead-status
```

---

## Configuration Resilience4j

### Circuit Breaker (order-service)

| Paramètre                          | Valeur      |
| ---------------------------------- | ----------- |
| sliding-window-size                | 10 appels   |
| minimum-number-of-calls            | 5           |
| failure-rate-threshold             | 50%         |
| wait-duration-in-open-state        | 15 secondes |
| permitted-calls-in-half-open-state | 2           |

### Bulkhead (inventory-service)

| Bulkhead     | max-concurrent-calls | max-wait-duration |
| ------------ | -------------------- | ----------------- |
| readStock    | 10                   | 50ms              |
| reserveStock | 5                    | 200ms             |

---

## Endpoints Actuator

```
GET http://localhost:8081/actuator/circuitbreakers
GET http://localhost:8081/actuator/health
GET http://localhost:8082/actuator/bulkheads
GET http://localhost:8082/actuator/health
```

---

## Structure du projet

```
ecommerce-resilience/
├── order-service/          ← Circuit Breaker (Spring Boot)
│   ├── src/main/java/com/ecommerce/order/
│   │   ├── client/PaymentClient.java     ← @CircuitBreaker
│   │   ├── controller/OrderController.java
│   │   ├── service/OrderService.java
│   │   └── model/, dto/, config/, exception/
│   └── src/main/resources/application.yml
├── inventory-service/      ← Bulkhead (Spring Boot)
│   ├── src/main/java/com/ecommerce/inventory/
│   │   ├── service/InventoryService.java  ← @Bulkhead x2
│   │   └── controller/, model/, dto/
│   └── src/main/resources/application.yml
├── payment-mock/           ← API paiement simulée
│   └── src/main/java/com/ecommerce/payment/
│       └── controller/PaymentController.java
├── nginx/
│   └── nginx.conf          ← Reverse Proxy + Round-Robin LB
└── scripts/
    ├── start.bat / start.sh
    └── test.ps1  / test.sh
```
