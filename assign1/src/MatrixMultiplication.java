import java.util.Scanner;
import java.util.Random;

public class MatrixMultiplication {

    public static void onMult(int size) {
        double[][] matrixA = new double[size][size];
        double[][] matrixB = new double[size][size];
        double[][] matrixC = new double[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrixA[i][j] = 1.0;
                matrixB[i][j] = i + 1;
            }
        }

        long startTime = System.nanoTime();

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                double temp = 0;
                for (int k = 0; k < size; k++) {
                    temp += matrixA[i][k] * matrixB[k][j];
                }
                matrixC[i][j] = temp;
            }
        }

        long endTime = System.nanoTime();
        System.out.printf("Time: %.3f seconds\n", (endTime - startTime) / 1e9);

        System.out.print("Result matrix: ");
        for (int j = 0; j < Math.min(10, size); j++) {
            System.out.print(matrixC[0][j] + " ");
        }
        System.out.println();
    }

    public static void onMultLine(int size) {
        double[][] matrixA = new double[size][size];
        double[][] matrixB = new double[size][size];
        double[][] matrixC = new double[size][size];

        // Initialize matrices
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrixA[i][j] = 1.0;
                matrixB[i][j] = i + 1;
            }
        }

        long startTime = System.nanoTime();

        for (int i = 0; i < size; i++) {
            for (int k = 0; k < size; k++) {
                double temp = matrixA[i][k]; 
                for (int j = 0; j < size; j++) {
                    matrixC[i][j] += temp * matrixB[k][j];
                }
            }
        }

        long endTime = System.nanoTime();
        System.out.printf("Time: %.3f seconds\n", (endTime - startTime) / 1e9);

        System.out.print("Result matrix: ");
        for (int j = 0; j < Math.min(10, size); j++) {
            System.out.print(matrixC[0][j] + " ");
        }
        System.out.println();
    }

    public static void onMultBlock(int size, int bkSize) {
        double[] matrixA = new double[size * size];
        double[] matrixB = new double[size * size];
        double[] matrixC = new double[size * size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrixA[i * size + j] = 1.0;
                matrixB[i * size + j] = i + 1;
                matrixC[i * size + j] = 0.0;
            }
        }

        long startTime = System.nanoTime();

        for (int ii = 0; ii < size; ii += bkSize) {
            for (int kk = 0; kk < size; kk += bkSize) {
                for (int jj = 0; jj < size; jj += bkSize) {

                    for (int i = ii; i < Math.min(ii + bkSize, size); i++) {
                        for (int k = kk; k < Math.min(kk + bkSize, size); k++) {
                            double temp = matrixA[i * size + k]; 

                            for (int j = jj; j < Math.min(jj + bkSize, size); j++) {
                                matrixC[i * size + j] += temp * matrixB[k * size + j];
                            }
                        }
                    }
                }
            }
        }

        long endTime = System.nanoTime();
        System.out.printf("Time: %.3f seconds\n", (endTime - startTime) / 1e9);

        System.out.print("Result matrix: ");
        for (int j = 0; j < Math.min(10, size); j++) {
            System.out.print(matrixC[j] + " ");
        }
        System.out.println();
    }


    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int option, size, blockSize;

        do {
            System.out.println("\n1. Multiplication");
            System.out.println("2. Line Multiplication");
            System.out.println("3. Block Multiplication");
            System.out.print("Selection?: ");
            option = scanner.nextInt();

            if (option == 0) break;

            System.out.print("Dimensions (n x n): ");
            size = scanner.nextInt();

            switch (option) {
                case 1:
                    onMult(size);
                    break;
                case 2:
                    onMultLine(size);
                    break;
                case 3:
                    System.out.print("Block Size?: ");
                    blockSize = scanner.nextInt();
                    onMultBlock(size, blockSize);
                    break;
                default:
                    System.out.println("Invalid option.");
            }

        } while (option != 0);

        scanner.close();
    }
}