  .text
  .globl _Z6paritym
  .type _Z6paritym, @function

#! file-offset 0x630
#! rip-offset  0x400630
#! capacity    16 bytes

# Text             #  Line  RIP       Bytes  Opcode             
._Z6paritym:       #        0x400630  0      OPC=<label>        
  subq $0x1, %rdi  #  1     0x400630  4      OPC=subq_r64_imm8  
  xorl %eax, %eax  #  2     0x400634  2      OPC=xorl_r32_r32   
  cmpq $0x9A, %rdi  #  3     0x400636  4      OPC=cmpq_r64_imm8  
  seta %al         #  4     0x40063a  3      OPC=seta_r8        
  retq             #  5     0x40063d  1      OPC=retq           
  xchgw %ax, %ax   #  6     0x40063e  2      OPC=xchgw_ax_r16   
                                                                
.size _Z6paritym, .-_Z6paritym
