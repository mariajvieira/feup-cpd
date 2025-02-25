import java.util.Scanner;
import java.util.Random;

public class MatrixMultiplication {
    
    public static void onMult(int size) {
        double[][] matrixA = new double[size][size];
        double[][] matrixB = new double[size][size];
        double[][] matrixC = new double[size][size];
        
        // Inicializar matrizes
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrixA[i][j] = 1.0;
                matrixB[i][j] = i + 1;
            }
        }
        
        long startTime = System.nanoTime();
        
        // Multiplicação de matrizes
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
        
        // Exibir os primeiros 10 elementos da matriz resultado
        System.out.print("Result matrix: ");
        for (int j = 0; j < Math.min(10, size); j++) {
            System.out.print(matrixC[0][j] + " ");
        }
        System.out.println();
    }
    
    public static void onMultLine(int size) {
        Random rand = new Random();
        double[][] matrixA = new double[size][size];
        double[][] matrixB = new double[size][size];
        double[][] matrixC = new double[size][size];
        
        // Inicializar matrizes
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrixA[i][j] = rand.nextInt(10);
                matrixB[i][j] = rand.nextInt(10);
            }
        }
        
        long startTime = System.nanoTime();
        
        // Multiplicação linha a linha
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrixC[i][j] = 0;
                for (int k = 0; k < size; k++) {
                    matrixC[i][j] += matrixA[i][k] * matrixB[k][j];
                }
            }
        }
        
        long endTime = System.nanoTime();
        System.out.printf("Time: %.3f seconds\n", (endTime - startTime) / 1e9);
    }
    
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int option, size;
        
        do {
            System.out.println("\n1. Multiplication");
            System.out.println("2. Line Multiplication");
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
                default:
                    System.out.println("Invalid option.");
            }
            
        } while (option != 0);
        
        scanner.close();
    }
}
