# CODIS

CODIS is an inductive program synthesizer.

## Installation

0. Install Maven 3, oracle-java8-installer. 
1. Compile Z3 with Java support.
2. Install `com.microsoft.z3` to Maven:

        mvn install:install-file -Dfile=/path/to/com.microsoft.z3.jar \
                                 -DgroupId=com.microsoft.z3 \
                                 -DartifactId=z3 \
                                 -Dversion=4 \
                                 -Dpackaging=jar
3. Compile MathSAT with Java support.
4. Install `mathsat.api` to Maven:

        mvn install:install-file -Dfile=/path/to/mathsat.jar \
                                 -DgroupId=mathsat.api \
                                 -DartifactId=mathsat \
                                 -Dversion=5 \
                                 -Dpackaging=jar
5. Execute `mvn package`.
6. Run test: `mvn test -Dtest=TestStoke`

## Extractor
 
 0. Compile using clang-3.5.
 
        clang++-3.5 -std=c++11 extractor.cpp -o extractor
 1. Run: ./extractor input_assembly_file function_name
 
        ./extractor _Z6paritym.s _Z6paritym
