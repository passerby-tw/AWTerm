import java.nio.file.*;
import java.util.jar.*;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.ConstantInstruction;

/** Use JediTerm's existing NUL/background path only for synthetic unused line tails. */
public final class PatchTail {
  public static void main(String[] args)throws Exception {
    String name="com/jediterm/terminal/ui/TerminalPanel$6";
    byte[] bytes;try(JarFile jar=new JarFile(args[0])){bytes=jar.getInputStream(jar.getJarEntry(name+".class")).readAllBytes();}
    ClassFile classFile=ClassFile.of();int[] targets={0};
    byte[] patched=classFile.transformClass(classFile.parse(bytes),
        ClassTransform.transformingMethodBodies(
            method -> method.methodName().equalsString("consumeQueue")
                &&method.methodType().equalsString("(IIII)V"),
            (builder,element) -> {
              if(element instanceof ConstantInstruction.ArgumentConstantInstruction constant
                  &&constant.opcode()==Opcode.BIPUSH&&constant.constantValue()==32) {
                targets[0]++;builder.iconst_0();
              } else builder.with(element);
            }));
    if(targets[0]!=1)throw new IllegalStateException("Expected one synthetic tail space target");
    Path path=Path.of(args[1],name+".class");Files.createDirectories(path.getParent());Files.write(path,patched);
  }
}
