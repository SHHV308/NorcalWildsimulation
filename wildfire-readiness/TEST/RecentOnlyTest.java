import java.time.*; import java.util.*;
public class RecentOnlyTest {
  static ObservedFire f(String id, Instant t){ return new ObservedFire(ObservedFire.Source.NASA_FIRMS_DETECTION,id,"x",t,Instant.now(),39.0,-121.0,null);}
  public static void main(String[] a){
    Instant now=Instant.parse("2026-10-27T01:00:00Z");
    List<ObservedFire> in=List.of(f("new",now.minus(Duration.ofHours(2))), f("edge",now.minus(Duration.ofHours(24))),
        f("old",now.minus(Duration.ofHours(30))), f("none",null), f("future",now.plus(Duration.ofMinutes(5))));
    List<String> ids=new ArrayList<>(); for(ObservedFire o: FireDataApiClient.recentOnly(in,now,Duration.ofHours(24))) ids.add(o.getId());
    System.out.println(ids);
    if(!ids.equals(List.of("new","edge","none","future"))) throw new AssertionError("unexpected "+ids);
    System.out.println("recentOnly OK");
  }
}
