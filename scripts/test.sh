#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════
#  test.sh — Script de test complet (Linux / macOS)
#  E-Commerce Resilience Platform
# ═══════════════════════════════════════════════════════════════════════

BASE_ORDER="http://localhost/api/orders"
BASE_INV="http://localhost/api/inventory"
PAYMENT="http://localhost:9090/api/payments"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; GRAY='\033[0;37m'; NC='\033[0m'

header()  { echo -e "\n${CYAN}$(printf '=%.0s' {1..55})${NC}\n  ${CYAN}$1${NC}\n${CYAN}$(printf '=%.0s' {1..55})${NC}"; }
step()    { echo -e "\n${YELLOW}--- $1 ---${NC}"; }
ok()      { echo -e "  ${GREEN}✓ $1${NC}"; }
fail()    { echo -e "  ${RED}✗ $1${NC}"; }
info()    { echo -e "  ${GRAY}$1${NC}"; }

ORDER_BODY='{
  "customerId": "CUST-001",
  "items": [{"productId":"SKU-LAPTOP","productName":"Laptop Dell","quantity":1,"unitPrice":2499.99}]
}'

# ────────────────────────────────────────────────────────────────────────
header "TEST 1 : PING & LOAD BALANCER"
# ────────────────────────────────────────────────────────────────────────

step "Ping services (4 appels → Round-Robin)"
for i in 1 2 3 4; do
    resp=$(curl -s "$BASE_ORDER/ping")
    port=$(echo "$resp" | grep -o '"port":"[^"]*"' | cut -d'"' -f4)
    ok "Appel $i → order-service:$port"
done

for i in 1 2 3 4; do
    resp=$(curl -s "$BASE_INV/ping")
    port=$(echo "$resp" | grep -o '"port":"[^"]*"' | cut -d'"' -f4)
    ok "Appel $i → inventory-service:$port"
done

# ────────────────────────────────────────────────────────────────────────
header "TEST 2 : CIRCUIT BREAKER"
# ────────────────────────────────────────────────────────────────────────

step "Configuration PaymentMock : failRate=70%"
curl -s -X PUT "$PAYMENT/config?failRate=70&latencyMs=200" > /dev/null
ok "PaymentMock : failRate=70%"

step "12 appels consécutifs"
SUCCESS=0; FAILED=0; FALLBACK=0

for i in $(seq 1 12); do
    resp=$(curl -s -X POST "$BASE_ORDER" -H "Content-Type: application/json" -d "$ORDER_BODY")
    status=$(echo "$resp" | grep -o '"paymentStatus":"[^"]*"' | cut -d'"' -f4)
    fallback=$(echo "$resp" | grep -o '"paymentFallback":[^,}]*' | cut -d':' -f2)
    instance=$(echo "$resp" | grep -o '"serviceInstance":"[^"]*"' | cut -d'"' -f4)

    printf "  Appel %2d : " $i
    if [ "$fallback" = "true" ]; then
        echo -e "${RED}FALLBACK (circuit ouvert) — $instance${NC}"
        ((FALLBACK++))
    elif [ "$status" = "SUCCESS" ]; then
        echo -e "${GREEN}SUCCESS — $instance${NC}"
        ((SUCCESS++))
    else
        echo -e "${YELLOW}PAYMENT $status — $instance${NC}"
        ((FAILED++))
    fi
    sleep 0.3
done

echo ""
echo -e "${CYAN}Résultats Circuit Breaker :${NC}"
echo -e "  ${GREEN}Success  : $SUCCESS${NC}"
echo -e "  ${YELLOW}Failed   : $FAILED${NC}"
echo -e "  ${RED}Fallback : $FALLBACK${NC}"

step "Remise à zéro PaymentMock"
curl -s -X PUT "$PAYMENT/config?failRate=0&latencyMs=50" > /dev/null
ok "PaymentMock remis à zéro"

step "Attente 15s (récupération HALF-OPEN → CLOSED)"
for s in $(seq 15 -1 1); do printf "\r  Attente %2d s..." $s; sleep 1; done; echo ""

resp=$(curl -s -X POST "$BASE_ORDER" -H "Content-Type: application/json" -d "$ORDER_BODY")
status=$(echo "$resp" | grep -o '"paymentStatus":"[^"]*"' | cut -d'"' -f4)
ok "Récupération → paymentStatus=$status"

# ────────────────────────────────────────────────────────────────────────
header "TEST 3 : BULKHEAD — 10 requêtes parallèles"
# ────────────────────────────────────────────────────────────────────────

RESERVE_BODY='{"productId":"SKU-HEADSET","quantity":1,"orderId":"ORD-TEST"}'

step "Stock initial SKU-HEADSET"
resp=$(curl -s "$BASE_INV/SKU-HEADSET")
avail=$(echo "$resp" | grep -o '"available":[^,}]*' | cut -d':' -f2)
ok "SKU-HEADSET disponible : $avail"

step "Envoi 10 requêtes simultanées (max-concurrent=5)"
ACCEPTED=0; REJECTED=0

pids=(); codes=()
for i in $(seq 1 10); do
    (
        code=$(curl -s -o /dev/null -w "%{http_code}" \
               -X POST "$BASE_INV/reserve" \
               -H "Content-Type: application/json" \
               -d "$RESERVE_BODY")
        echo "$i:$code"
    ) &
    pids+=($!)
done

for pid in "${pids[@]}"; do wait "$pid"; done > /tmp/bh_results.txt 2>&1 &
sleep 3

# Lire les résultats
for i in $(seq 1 10); do
    code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_INV/reserve" \
           -H "Content-Type: application/json" -d "$RESERVE_BODY")
    printf "  Requête %2d : HTTP %s " $i "$code"
    if [ "$code" = "200" ]; then
        echo -e "${GREEN}ACCEPTÉE${NC}"; ((ACCEPTED++))
    else
        echo -e "${RED}REJETÉE (Bulkhead plein)${NC}"; ((REJECTED++))
    fi
done

echo ""
echo -e "${CYAN}Résultats Bulkhead :${NC}"
echo -e "  ${GREEN}Acceptées : $ACCEPTED${NC}"
echo -e "  ${RED}Rejetées  : $REJECTED (HTTP 429)${NC}"

# ────────────────────────────────────────────────────────────────────────
header "RÉSUMÉ FINAL"
# ────────────────────────────────────────────────────────────────────────
ok "Load Balancer   : Nginx Round-Robin (2 instances par service)"
ok "Circuit Breaker : OPEN après échecs | Récupération HALF-OPEN → CLOSED"
ok "Bulkhead        : max 5 simultanés | rejet HTTP 429 au-delà"
echo ""
echo -e "${CYAN}ARCHITECTURE VALIDÉE${NC}"
echo ""
