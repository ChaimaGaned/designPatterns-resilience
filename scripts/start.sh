#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════
#  start.sh — Démarrage de l'architecture complète
#  E-Commerce Resilience Platform
# ═══════════════════════════════════════════════════════════════════════

set -e
CYAN='\033[0;36m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

echo -e "\n${CYAN}==========================================${NC}"
echo -e "${CYAN}  E-COMMERCE RESILIENCE PLATFORM${NC}"
echo -e "${CYAN}==========================================${NC}\n"

# ── 1. Build ─────────────────────────────────────────────────────────────
echo -e "${YELLOW}[1/5] Build order-service...${NC}"
cd order-service && mvn clean package -q -DskipTests && cd ..

echo -e "${YELLOW}[2/5] Build inventory-service...${NC}"
cd inventory-service && mvn clean package -q -DskipTests && cd ..

echo -e "${YELLOW}[3/5] Build payment-mock...${NC}"
cd payment-mock && mvn clean package -q -DskipTests && cd ..

echo -e "\n${YELLOW}[4/5] Démarrage des 5 processus Java...${NC}\n"

# ── 2. Démarrage des instances ───────────────────────────────────────────
java -jar order-service/target/order-service-1.0.0.jar \
     --server.port=8081 \
     --payment.service.url=http://localhost:9090 > /tmp/order-8081.log 2>&1 &
echo -e "  ${GREEN}✓ OrderService-1    :8081 (PID $!)${NC}"

java -jar order-service/target/order-service-1.0.0.jar \
     --server.port=8083 \
     --payment.service.url=http://localhost:9090 > /tmp/order-8083.log 2>&1 &
echo -e "  ${GREEN}✓ OrderService-2    :8083 (PID $!)${NC}"

java -jar inventory-service/target/inventory-service-1.0.0.jar \
     --server.port=8082 > /tmp/inventory-8082.log 2>&1 &
echo -e "  ${GREEN}✓ InventoryService-1 :8082 (PID $!)${NC}"

java -jar inventory-service/target/inventory-service-1.0.0.jar \
     --server.port=8084 > /tmp/inventory-8084.log 2>&1 &
echo -e "  ${GREEN}✓ InventoryService-2 :8084 (PID $!)${NC}"

java -jar payment-mock/target/payment-mock-1.0.0.jar \
     --server.port=9090 > /tmp/payment.log 2>&1 &
echo -e "  ${GREEN}✓ PaymentMock        :9090 (PID $!)${NC}"

echo -e "\n${YELLOW}[5/5] Attente du démarrage (15 secondes)...${NC}"
sleep 15

echo -e "\n${CYAN}==========================================${NC}"
echo -e "${CYAN}  SERVICES DÉMARRÉS${NC}"
echo -e "${CYAN}==========================================${NC}"
echo -e "  Order Service      : http://localhost:8081 et :8083"
echo -e "  Inventory Service  : http://localhost:8082 et :8084"
echo -e "  Payment Mock       : http://localhost:9090"

# ── 3. Nginx ─────────────────────────────────────────────────────────────
if command -v nginx &> /dev/null; then
    echo -e "\n  Démarrage Nginx..."
    sudo nginx -c "$(pwd)/nginx/nginx.conf"
    echo -e "  ${GREEN}✓ Nginx sur http://localhost${NC}"
else
    echo -e "\n  ${YELLOW}⚠ Nginx non trouvé — installez avec :${NC}"
    echo -e "    Ubuntu/Debian : sudo apt install nginx"
    echo -e "    macOS         : brew install nginx"
fi

echo -e "\n  Pour tester : bash scripts/test.sh\n"
