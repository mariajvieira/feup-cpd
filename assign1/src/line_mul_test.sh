#!/bin/bash

# Compilar o programa (caso necessário)
g++ -o matrixproduct matrixproduct.cpp -lpapi

# Verificar se a compilação foi bem-sucedida
if [ ! -f "matrixproduct" ]; then
    echo "Erro: Falha na compilação." >&2
    exit 1
fi

# Definir os tamanhos das matrizes
sizes=(600 1000 1400 1800 2200 2600 3000 4096 6144 8192 10240)

# Arquivo de saída
output_file="matrixproduct_results.txt"
echo "Resultados da Multiplicação de Matrizes (Line Multiplication)" > "$output_file"

echo "Iniciando testes..."

# Loop para testar cada tamanho
for size in "${sizes[@]}"; do
    echo "Testando tamanho: $size" | tee -a "$output_file"
    for i in {1..5}; do
        echo "Execução $i:" >> "$output_file"
        echo "$size" | ./matrix_multiplication 2>> "$output_file" >> "$output_file"
        echo "----------------------" >> "$output_file"
    done
    echo "" >> "$output_file"
done

echo "Testes concluídos. Resultados armazenados em $output_file"
