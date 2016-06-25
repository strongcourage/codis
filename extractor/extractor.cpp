#include <string>
#include <iostream>
#include <fstream>
#include <vector>

using namespace std;

// find position of first occurrence of special character
static inline int first_position(string str, const char c) {
    int pos = -1;
    for (int i=0; i<str.size(); i++) {
        if (str.at(i) == c) {
            pos = i;
            break;
        }
    }
    return pos;
}

// find position of occurrence of special character after a specific position
static inline int position_after(string str, const char c, int other_pos) {
    int pos = -1;
    for (int i=0; i<str.size(); i++) {
        if (str.at(i) == c && i > other_pos) {
            pos = i;
            break;
        }
    }
    return pos;
}

static void extractor(string fileName, string funcName) {
    ifstream ifs(fileName);
    string line;
    int count = 0;
    vector<string> constant;

    // open and read objdump input file
    if (ifs.is_open()) {
        while(getline(ifs, line)) {
            count++;
            int pos_$ = first_position(line, '$'); 
            if (count > 10 && pos_$ >= 0) {
                // cout << count << ":" << line << endl; 
                int pos_space = position_after(line, ' ', pos_$);
                string const_str = line.substr(pos_$+3, pos_space-pos_$-3);
                string const_val = "";
                for (int i=0; i<const_str.size(); ++i) {
                    if ((const_str[i] >= 48 && const_str[i] <= 57) || 
                        (const_str[i] >= 65 && const_str[i] <= 70)) {
                        const_val += const_str[i];
                    }
                }
                cout << const_val << endl;
                constant.push_back(const_val);
            }  
            size_t pos_end_line = line.find(".size");
            if (pos_end_line != string::npos) {
                break;
            }    
        }

        // close file
        ifs.close();
    } else {
        cout << "Unable to open objdump input file";
        exit(-1);
    }  
}

int main(int argc, char **argv) {
    if (argc != 3) {
        cout << "Usage: ./extractor input_assembly_file function_name!" << endl;
        return -1;
    }

    string fileName(argv[1]);
    string funcName(argv[2]);
    extractor(fileName, funcName);

    return 0;
}