package org.awterm;

import com.jediterm.terminal.TtyConnector;
import com.jediterm.core.util.TermSize;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import org.espressh.vts.TerminalEndpoint;

/** JediTerm IO over the shared VTS API; no Espresso or kernel implementation dependency. */
public final class VtsConnector implements TtyConnector {
  private final TerminalEndpoint endpoint;
  private final Reader reader;
  private final CountDownLatch ended=new CountDownLatch(1);
  private volatile boolean connected=true;
  private volatile boolean frontendClosed;
  public VtsConnector(TerminalEndpoint endpoint) {
    this.endpoint=java.util.Objects.requireNonNull(endpoint);
    reader=new InputStreamReader(endpoint.output(),StandardCharsets.UTF_8);
  }
  @Override public int read(char[] bytes,int offset,int length)throws IOException {
    try {
      int n=reader.read(bytes,offset,length);
      if(n<0) streamEnded();
      return n;
    } catch(IOException failure) {streamEnded();throw failure;}
  }
  @Override public void write(byte[] bytes)throws IOException {
    int start=0;
    for(int i=0;i<bytes.length;i++) if(bytes[i]==3) {
      if(i>start) endpoint.input().write(bytes,start,i-start);
      endpoint.interrupt();
      start=i+1;
    }
    if(start<bytes.length) endpoint.input().write(bytes,start,bytes.length-start);
  }
  @Override public void write(String text)throws IOException { write(text.getBytes(StandardCharsets.UTF_8)); }
  @Override public boolean isConnected(){return connected;}
  @Override public boolean ready()throws IOException {
    try {return reader.ready();}
    catch(IOException failure) {streamEnded();throw failure;}
  }
  private void streamEnded(){connected=false;ended.countDown();}
  @Override public void resize(TermSize size){endpoint.resize(size.getColumns(),size.getRows());}
  @Override public String getName(){return "VTS";}
  @Override public int waitFor()throws InterruptedException {ended.await();return 0;}
  public void interrupt(){endpoint.interrupt();}
  /** UI must obtain the last-frontend decision before calling dispose/close. */
  public synchronized long requestClose(){
    if(frontendClosed)return 0;
    long token=endpoint.requestClose();
    if(token==0) markClosed();
    return token;
  }
  public synchronized void decideClose(long token,boolean shutdown){
    endpoint.decideClose(token,shutdown);
    if(shutdown)markClosed();
  }
  private void markClosed(){frontendClosed=true;connected=false;ended.countDown();}
  @Override public void close(){
    // A shell-initiated VT close already revoked this endpoint. Owner cleanup
    // handles transport loss; never issue a fresh normal close on a dead VT.
    if(frontendClosed || !connected)return;
    long token=requestClose();
    if(token!=0)return; // Preserve the frontend until UI asks Shutdown/Continue.
    connected=false;ended.countDown();
  }
}
