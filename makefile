run: 
	g++ -O2 assign1/src/matrixproduct.cpp -o matrix -fopenmp -lpapi
	./matrix
