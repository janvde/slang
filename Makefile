# Slang Makefile
# Provides convenient targets for building and running Slang programs

.PHONY: help build test clean run compile install

# Default target
help:
	@echo "Slang Project - Available targets:"
	@echo ""
	@echo "  make build         - Build the Slang compiler"
	@echo "  make test          - Run all tests"
	@echo "  make clean         - Clean build artifacts"
	@echo "  make install       - Install slangc to /usr/local/bin"
	@echo "  make compile FILE=<file.slang> [OUT=<output>]"
	@echo "                     - Compile a .slang file to executable"
	@echo "  make run FILE=<file.slang>"
	@echo "                     - Compile and run a .slang file"
	@echo ""
	@echo "Examples:"
	@echo "  make build"
	@echo "  make compile FILE=src/main/resources/example.slang"
	@echo "  make compile FILE=hello.slang OUT=hello"
	@echo "  make run FILE=src/main/resources/example.slang"
	@echo ""

# Build the compiler
build:
	@echo "Building Slang compiler..."
	./gradlew build

# Run tests
test:
	@echo "Running tests..."
	./gradlew test

# Clean build artifacts
clean:
	@echo "Cleaning build artifacts..."
	./gradlew clean
	rm -f output output.ll output.o
	rm -f *.ll *.o

# Compile a Slang file
compile:
ifndef FILE
	$(error FILE is not set. Usage: make compile FILE=example.slang [OUT=output])
endif
	@echo "Compiling $(FILE)..."
	@./slangc $(FILE) $(if $(OUT),$(OUT),output)

# Compile and run a Slang file
run:
ifndef FILE
	$(error FILE is not set. Usage: make run FILE=example.slang)
endif
	@echo "Compiling and running $(FILE)..."
	@./slangc $(FILE) $(if $(OUT),$(OUT),output) --run

# Install slangc to system
install:
	@echo "Installing slangc to /usr/local/bin..."
	@if [ -w /usr/local/bin ]; then \
		cp slangc /usr/local/bin/slangc; \
		cp slangc.py /usr/local/bin/slangc.py; \
		chmod +x /usr/local/bin/slangc; \
		chmod +x /usr/local/bin/slangc.py; \
		echo "Installation successful!"; \
		echo "You can now use 'slangc' from anywhere"; \
	else \
		echo "Error: /usr/local/bin is not writable"; \
		echo "Run: sudo make install"; \
	fi

# Quick examples
examples: build
	@echo "Running example programs..."
	@echo "\n=== Example 1: Basic arithmetic ==="
	@./slangc src/main/resources/example.slang --run || true
	@echo "\n=== Example 2: Comparison operators ==="
	@./slangc src/main/resources/comparison_demo.slang --run || true
	@echo "\n=== Example 3: Features demo ==="
	@./slangc src/main/resources/features_demo.slang --run || true

# Development targets
dev-build:
	./gradlew build -x test

watch:
	./gradlew build --continuous

# Distribution
dist: build
	@echo "Creating distribution..."
	./gradlew distTar distZip
	@echo "Distribution archives created in build/distributions/"

