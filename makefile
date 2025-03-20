run: 
	g++ -O2 assign1/src/matrixproduct.cpp -o matrix -lpapi
	./matrix

parallel: 
	g++ -fopenmp -O2 assign1/src/parallelmatrixproduct.cpp -o parallel
	./parallel