	.file	"transpiler.cpp"
	.intel_syntax noprefix
	.text
#APP
	.globl _ZSt21ios_base_library_initv
#NO_APP
	.section	.text._ZNKSt5ctypeIcE8do_widenEc,"axG",@progbits,_ZNKSt5ctypeIcE8do_widenEc,comdat
	.align 2
	.p2align 4
	.weak	_ZNKSt5ctypeIcE8do_widenEc
	.type	_ZNKSt5ctypeIcE8do_widenEc, @function
_ZNKSt5ctypeIcE8do_widenEc:
.LFB1840:
	.cfi_startproc
	endbr64
	mov	eax, esi
	ret
	.cfi_endproc
.LFE1840:
	.size	_ZNKSt5ctypeIcE8do_widenEc, .-_ZNKSt5ctypeIcE8do_widenEc
	.section	.text.unlikely,"ax",@progbits
.LCOLDB0:
	.text
.LHOTB0:
	.p2align 4
	.type	_ZSt4endlIcSt11char_traitsIcEERSt13basic_ostreamIT_T0_ES6_.isra.0, @function
_ZSt4endlIcSt11char_traitsIcEERSt13basic_ostreamIT_T0_ES6_.isra.0:
.LFB2615:
	.cfi_startproc
	push	rbp
	.cfi_def_cfa_offset 16
	.cfi_offset 6, -16
	push	rbx
	.cfi_def_cfa_offset 24
	.cfi_offset 3, -24
	sub	rsp, 8
	.cfi_def_cfa_offset 32
	mov	rax, QWORD PTR [rdi]
	mov	rax, QWORD PTR -24[rax]
	mov	rbp, QWORD PTR 240[rdi+rax]
	test	rbp, rbp
	je	.L8
	cmp	BYTE PTR 56[rbp], 0
	mov	rbx, rdi
	je	.L5
	movsx	esi, BYTE PTR 67[rbp]
.L6:
	mov	rdi, rbx
	call	_ZNSo3putEc@PLT
	add	rsp, 8
	.cfi_remember_state
	.cfi_def_cfa_offset 24
	pop	rbx
	.cfi_def_cfa_offset 16
	mov	rdi, rax
	pop	rbp
	.cfi_def_cfa_offset 8
	jmp	_ZNSo5flushEv@PLT
.L5:
	.cfi_restore_state
	mov	rdi, rbp
	call	_ZNKSt5ctypeIcE13_M_widen_initEv@PLT
	mov	rax, QWORD PTR 0[rbp]
	mov	esi, 10
	lea	rdx, _ZNKSt5ctypeIcE8do_widenEc[rip]
	mov	rax, QWORD PTR 48[rax]
	cmp	rax, rdx
	je	.L6
	mov	esi, 10
	mov	rdi, rbp
	call	rax
	movsx	esi, al
	jmp	.L6
	.cfi_endproc
	.section	.text.unlikely
	.cfi_startproc
	.type	_ZSt4endlIcSt11char_traitsIcEERSt13basic_ostreamIT_T0_ES6_.isra.0.cold, @function
_ZSt4endlIcSt11char_traitsIcEERSt13basic_ostreamIT_T0_ES6_.isra.0.cold:
.LFSB2615:
.L8:
	.cfi_def_cfa_offset 32
	.cfi_offset 3, -24
	.cfi_offset 6, -16
	call	_ZSt16__throw_bad_castv@PLT
	.cfi_endproc
.LFE2615:
	.text
	.size	_ZSt4endlIcSt11char_traitsIcEERSt13basic_ostreamIT_T0_ES6_.isra.0, .-_ZSt4endlIcSt11char_traitsIcEERSt13basic_ostreamIT_T0_ES6_.isra.0
	.section	.text.unlikely
	.size	_ZSt4endlIcSt11char_traitsIcEERSt13basic_ostreamIT_T0_ES6_.isra.0.cold, .-_ZSt4endlIcSt11char_traitsIcEERSt13basic_ostreamIT_T0_ES6_.isra.0.cold
.LCOLDE0:
	.text
.LHOTE0:
	.p2align 4
	.globl	_Z4initv
	.type	_Z4initv, @function
_Z4initv:
.LFB2087:
	.cfi_startproc
	endbr64
	sub	rsp, 8
	.cfi_def_cfa_offset 16
	mov	edi, 1024
	call	malloc@PLT
	mov	edi, 1024
	mov	QWORD PTR slab4[rip], rax
	call	malloc@PLT
	mov	edi, 1024
	mov	QWORD PTR slab8[rip], rax
	call	malloc@PLT
	mov	edi, 1024
	mov	QWORD PTR slab16[rip], rax
	call	malloc@PLT
	mov	edi, 1024
	mov	QWORD PTR slab32[rip], rax
	call	malloc@PLT
	mov	edi, 1024
	mov	QWORD PTR slab64[rip], rax
	call	malloc@PLT
	mov	QWORD PTR slab128[rip], rax
	add	rsp, 8
	.cfi_def_cfa_offset 8
	ret
	.cfi_endproc
.LFE2087:
	.size	_Z4initv, .-_Z4initv
	.p2align 4
	.globl	_Z7cleanupv
	.type	_Z7cleanupv, @function
_Z7cleanupv:
.LFB2088:
	.cfi_startproc
	endbr64
	sub	rsp, 8
	.cfi_def_cfa_offset 16
	mov	rdi, QWORD PTR slab4[rip]
	call	free@PLT
	mov	rdi, QWORD PTR slab8[rip]
	call	free@PLT
	mov	rdi, QWORD PTR slab16[rip]
	call	free@PLT
	mov	rdi, QWORD PTR slab32[rip]
	call	free@PLT
	mov	rdi, QWORD PTR slab64[rip]
	call	free@PLT
	mov	rdi, QWORD PTR slab128[rip]
	add	rsp, 8
	.cfi_def_cfa_offset 8
	jmp	free@PLT
	.cfi_endproc
.LFE2088:
	.size	_Z7cleanupv, .-_Z7cleanupv
	.section	.text.startup,"ax",@progbits
	.p2align 4
	.globl	main
	.type	main, @function
main:
.LFB2089:
	.cfi_startproc
	endbr64
	push	rbx
	.cfi_def_cfa_offset 16
	.cfi_offset 3, -16
	lea	rbx, _ZSt4cout[rip]
	call	_Z4initv
	mov	rax, QWORD PTR slab4[rip]
	mov	esi, 2
	mov	rdi, rbx
	mov	DWORD PTR [rax], 2
	call	_ZNSolsEi@PLT
	mov	rdi, rax
	call	_ZSt4endlIcSt11char_traitsIcEERSt13basic_ostreamIT_T0_ES6_.isra.0
	mov	rdx, QWORD PTR .LC1[rip]
	mov	esi, 30
	mov	rdi, rbx
	mov	rax, QWORD PTR slab4[rip]
	mov	QWORD PTR 4[rax], rdx
	mov	DWORD PTR 12[rax], 30
	call	_ZNSolsEi@PLT
	mov	rdi, rax
	call	_ZSt4endlIcSt11char_traitsIcEERSt13basic_ostreamIT_T0_ES6_.isra.0
	mov	rdx, QWORD PTR .LC2[rip]
	mov	esi, 12
	mov	rdi, rbx
	mov	rax, QWORD PTR slab16[rip]
	mov	QWORD PTR [rax], rdx
	call	_ZNSolsEi@PLT
	mov	rdi, rax
	call	_ZSt4endlIcSt11char_traitsIcEERSt13basic_ostreamIT_T0_ES6_.isra.0
	call	_Z7cleanupv
	xor	eax, eax
	pop	rbx
	.cfi_def_cfa_offset 8
	ret
	.cfi_endproc
.LFE2089:
	.size	main, .-main
	.globl	slab128
	.bss
	.align 8
	.type	slab128, @object
	.size	slab128, 8
slab128:
	.zero	8
	.globl	slab64
	.align 8
	.type	slab64, @object
	.size	slab64, 8
slab64:
	.zero	8
	.globl	slab32
	.align 8
	.type	slab32, @object
	.size	slab32, 8
slab32:
	.zero	8
	.globl	slab16
	.align 8
	.type	slab16, @object
	.size	slab16, 8
slab16:
	.zero	8
	.globl	slab8
	.align 8
	.type	slab8, @object
	.size	slab8, 8
slab8:
	.zero	8
	.globl	slab4
	.align 8
	.type	slab4, @object
	.size	slab4, 8
slab4:
	.zero	8
	.section	.rodata.cst8,"aM",@progbits,8
	.align 8
.LC1:
	.long	10
	.long	20
	.align 8
.LC2:
	.long	5
	.long	7
	.ident	"GCC: (Ubuntu 14.2.0-4ubuntu2~24.04.1) 14.2.0"
	.section	.note.GNU-stack,"",@progbits
	.section	.note.gnu.property,"a"
	.align 8
	.long	1f - 0f
	.long	4f - 1f
	.long	5
0:
	.string	"GNU"
1:
	.align 8
	.long	0xc0000002
	.long	3f - 2f
2:
	.long	0x3
3:
	.align 8
4:
