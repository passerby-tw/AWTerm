import java.nio.file.*;
import java.util.jar.*;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

/** Host JDK25 build-time patch for checksum-pinned JediTerm 3.76. */
public final class PatchCursor {
  public static void main(String[] args) throws Exception {
    String name="com/jediterm/terminal/ui/TerminalPanel$TerminalCursor";
    byte[] original;
    try(JarFile jar=new JarFile(args[0])) {original=jar.getInputStream(jar.getJarEntry(name+".class")).readAllBytes();}
    ClassFile classFile=ClassFile.of(ClassFile.StackMapsOption.GENERATE_STACK_MAPS);
    int[] matched={0};
    byte[] patched=classFile.transformClass(classFile.parse(original),
        ClassTransform.transformingMethodBodies(method -> {
          boolean target=method.methodName().equalsString("changeStateIfNeeded")
              &&method.methodType().equalsString("()V");
          if(target)matched[0]++;
          return target;
        },new CodeTransform() {
          @Override public void atStart(CodeBuilder builder) {
            Label proceed=builder.newLabel();
            builder.aload(0)
                .invokevirtual(ClassDesc.ofInternalName(name),"isBlinking",MethodTypeDesc.ofDescriptor("()Z"))
                .ifne(proceed)
                .return_()
                .labelBinding(proceed);
          }
          @Override public void accept(CodeBuilder builder,CodeElement element) {
            builder.with(element);
          }
        }));
    if(matched[0]!=1)throw new IllegalStateException("Expected one JediTerm cursor guard target");
    Path path=Path.of(args[1],name+".class");Files.createDirectories(path.getParent());Files.write(path,patched);
  }
}
