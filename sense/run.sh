#!/bin/bash
# Sense Component - Run Script
# Usage: ./run.sh [--cf] [--build] [--gen]
#
# Options:
#   --cf      Deploy to Cloud Foundry using vars.yaml
#   --build   Build before running/deploying
#   --gen     Start the telematics generator (with RabbitMQ) before running
#   (none)    Run locally using vars.yaml

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VARS_FILE="${SCRIPT_DIR}/vars.yaml"
VARS_TEMPLATE="${SCRIPT_DIR}/vars.yaml.template"
TELEMATICS_GEN_DIR="${SCRIPT_DIR}/../../imc-telematics-gen"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if vars.yaml exists
check_vars_file() {
    if [[ ! -f "$VARS_FILE" ]]; then
        print_error "vars.yaml not found!"
        print_info "Please copy vars.yaml.template to vars.yaml and fill in your values:"
        echo ""
        echo "  cp vars.yaml.template vars.yaml"
        echo "  # Edit vars.yaml with your configuration"
        echo ""
        exit 1
    fi
}

# Build the application
build_app() {
    print_info "Building Sense component..."
    cd "$SCRIPT_DIR"
    ./mvnw clean package -DskipTests
    print_info "Build complete!"
}

# Start telematics generator (includes RabbitMQ)
start_telematics_gen() {
    if [[ ! -d "$TELEMATICS_GEN_DIR" ]]; then
        print_error "Telematics generator not found at: $TELEMATICS_GEN_DIR"
        print_info "Expected location: ../../imc-telematics-gen"
        exit 1
    fi

    print_info "Starting telematics generator (includes RabbitMQ)..."

    # Check if already running
    if [[ -f "$TELEMATICS_GEN_DIR/.pid" ]]; then
        GEN_PID=$(cat "$TELEMATICS_GEN_DIR/.pid")
        if ps -p "$GEN_PID" > /dev/null 2>&1; then
            print_info "Telematics generator already running (PID: $GEN_PID)"
            print_info "Dashboard: http://localhost:8082"
            return 0
        fi
    fi

    cd "$TELEMATICS_GEN_DIR"
    ./imc-telematics-gen.sh --start

    print_info "Telematics generator started!"
    print_info "  - Dashboard: http://localhost:8082"
    print_info "  - RabbitMQ Management: http://localhost:15672 (guest/guest)"
    print_info "  - Generating ~300 telemetry messages/second"
    echo ""

    cd "$SCRIPT_DIR"
}

# Run locally with vars.yaml
run_local() {
    check_vars_file
    print_info "Running Sense component locally..."
    print_info "Configuration will be loaded from vars.yaml via Spring Boot config import"

    cd "$SCRIPT_DIR"

    # Check if OpenAI API key is configured
    OPENAI_KEY=$(grep -A1 "openai:" "$VARS_FILE" | grep "api-key" | sed 's/.*: *"\(.*\)"/\1/' | head -1)

    if [[ -z "$OPENAI_KEY" || "$OPENAI_KEY" == "your-openai-api-key-here" ]]; then
        print_warn "OpenAI API key not configured in vars.yaml - AI classification will be disabled"
        ./mvnw spring-boot:run \
            -Dspring-boot.run.arguments="--sense.ai.intent-classification.enabled=false"
    else
        print_info "OpenAI API key found - AI classification enabled"
        ./mvnw spring-boot:run
    fi
}

# Deploy to Cloud Foundry
deploy_cf() {
    check_vars_file
    print_info "Deploying Sense component to Cloud Foundry..."

    cd "$SCRIPT_DIR"

    # Check if cf CLI is available
    if ! command -v cf &> /dev/null; then
        print_error "Cloud Foundry CLI (cf) not found. Please install it first."
        exit 1
    fi

    # Check if logged in
    if ! cf target &> /dev/null; then
        print_error "Not logged into Cloud Foundry. Please run 'cf login' first."
        exit 1
    fi

    # Push with vars file
    print_info "Pushing application with vars-file..."
    cf push --vars-file "$VARS_FILE"

    print_info "Deployment complete!"
    cf apps
}

# Parse arguments
BUILD=false
CF_MODE=false
START_GEN=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --build)
            BUILD=true
            shift
            ;;
        --cf)
            CF_MODE=true
            shift
            ;;
        --gen)
            START_GEN=true
            shift
            ;;
        --help|-h)
            echo "Sense Component - Run Script"
            echo ""
            echo "Usage: ./run.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --build    Build the application before running"
            echo "  --cf       Deploy to Cloud Foundry (uses vars.yaml)"
            echo "  --gen      Start telematics generator (includes RabbitMQ) before running"
            echo "  --help     Show this help message"
            echo ""
            echo "Examples:"
            echo "  ./run.sh                 # Run locally (requires RabbitMQ already running)"
            echo "  ./run.sh --gen           # Start generator + RabbitMQ, then run Sense"
            echo "  ./run.sh --build --gen   # Build, start generator, then run"
            echo "  ./run.sh --cf            # Deploy to Cloud Foundry"
            echo "  ./run.sh --build --cf    # Build and deploy to Cloud Foundry"
            echo ""
            echo "Endpoints when running locally:"
            echo "  Sense dashboard:     http://localhost:8081"
            echo "  Sense health:        http://localhost:8081/actuator/health"
            echo "  Sense metrics:       http://localhost:8081/actuator/prometheus"
            echo "  Generator dashboard: http://localhost:8082 (if --gen)"
            echo "  RabbitMQ UI:         http://localhost:15672 (guest/guest)"
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Execute based on options
if [[ "$BUILD" == true ]]; then
    build_app
fi

if [[ "$START_GEN" == true ]]; then
    start_telematics_gen
fi

if [[ "$CF_MODE" == true ]]; then
    deploy_cf
else
    run_local
fi
