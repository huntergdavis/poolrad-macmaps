package name.osher.gil.minivmac;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;
import name.osher.gil.minivmac.mapper.AreaIdentity;
import name.osher.gil.minivmac.notebook.AreaConnections;

/** A navigable graph of observed passages. Layout is schematic, never geography. */
public final class ConnectionsView extends ScrollView {
    private final LinearLayout content;
    private final TextView heading, hint, details;
    private final Button current;
    private final Graph graph;
    private final Spinner areas;
    private List<Integer> choices=Collections.emptyList();
    private boolean selecting;
    private AreaConnections history=new AreaConnections();
    private String book="Notebook loading", failure="";
    private int here=-1, selected=-1;
    private boolean follow=true;
    private final float density;
    public ConnectionsView(Context context) {
        super(context); density=getResources().getDisplayMetrics().density;
        setBackgroundColor(Color.WHITE); setFillViewport(true); setSmoothScrollingEnabled(false);
        setFocusable(false); setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        content=new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12),dp(8),dp(12),dp(8)); addView(content);
        LinearLayout top=new LinearLayout(context);
        heading=text(); top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        current=new Button(context); current.setText("Current area"); current.setAllCaps(false);
        current.setFocusable(false); current.setMinHeight(dp(48));
        current.setOnClickListener(v->{follow=true;selected=here;render();scrollTo(0,0);});
        top.addView(current); content.addView(top);
        areas=new Spinner(context); areas.setFocusable(false); areas.setFocusableInTouchMode(false); areas.setContentDescription("Browse discovered areas");
        content.addView(areas,new LinearLayout.LayoutParams(-1,dp(48)));
        areas.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View view,int index,long id){
                if(!selecting && index>=0 && index<choices.size() && choices.get(index)!=selected)choose(choices.get(index));
            }
        });
        hint=text(); content.addView(hint);
        graph=new Graph(context); content.addView(graph,new LinearLayout.LayoutParams(-1,dp(240)));
        details=text(); content.addView(details); render();
    }
    private int dp(int value){return Math.round(value*density);}
    private TextView text(){TextView view=new TextView(getContext());view.setTextColor(Color.BLACK);view.setTextSize(15);return view;}
    static String label(int id){return AreaIdentity.labelForId("por-mac-v11-geo-"+id);}
    private static String square(int tile){return tile%16+","+tile/16;}
    public void show(String book,AreaConnections history,String currentArea,String failure){
        int next=-1;
        if(currentArea!=null) try{next=Integer.parseInt(currentArea.substring("por-mac-v11-geo-".length()));}catch(RuntimeException ignored){}
        boolean changed=!this.book.equals(book) || this.history!=history || here!=next || !this.failure.equals(failure);
        if(!changed)return;
        if(!this.book.equals(book)){selected=-1;follow=true;}
        this.book=book;this.history=history;this.here=next;this.failure=failure;
        if(follow) selected=here;
        if(selected<0 && !history.edges.isEmpty()) selected=history.edges.get(0).fromArea;
        render();
    }
    private void choose(int area){selected=area;follow=false;render();scrollTo(0,0);}
    private void render(){
        heading.setText(book+" · Connections");current.setEnabled(here>=0);
        hint.setText(!failure.isEmpty()?failure:history.edges.isEmpty()
                ?"No connections discovered yet. Travel between areas to add them."
                :"Arrows show travel you made. Tap an area to see its connections.");
        TreeSet<Integer> known=new TreeSet<>();
        if(here>=0)known.add(here);
        for(AreaConnections.Edge e:history.edges){known.add(e.fromArea);known.add(e.toArea);}
        List<Integer> nextChoices=new ArrayList<>(known);
        selecting=true;
        if(!nextChoices.equals(choices)){
            choices=nextChoices;List<String> labels=new ArrayList<>();for(int area:choices)labels.add(label(area));
            ArrayAdapter<String> adapter=new ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_item,labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);areas.setAdapter(adapter);
        }
        if(choices.contains(selected))areas.setSelection(choices.indexOf(selected),false);
        areas.setVisibility(choices.isEmpty()?GONE:VISIBLE);selecting=false;
        TreeSet<Integer> neighbors=new TreeSet<>();StringBuilder routes=new StringBuilder();
        for(AreaConnections.Edge edge:history.edges){
            if(edge.fromArea==selected || edge.toArea==selected){
                neighbors.add(edge.fromArea==selected?edge.toArea:edge.fromArea);
                if(routes.length()>0)routes.append("\n\n");
                routes.append(label(edge.fromArea)).append(" ").append(square(edge.fromTile))
                        .append(" → ").append(label(edge.toArea)).append(" ").append(square(edge.toTile));
            }
        }
        details.setText(routes.length()>0?"Crossings recorded\n\n"+routes:"");
        details.setPadding(0,dp(12),0,0);
        graph.neighbors=new ArrayList<>(neighbors);
        graph.getLayoutParams().height=dp(Math.max(140,neighbors.size()*90+20));graph.requestLayout();graph.invalidate();
        graph.setContentDescription((selected<0?"No area selected":label(selected)+(selected==here?", current area":""))+". "+routes);
    }
    private final class Graph extends View {
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Map<Integer,RectF> targets=new LinkedHashMap<>();
        private List<Integer> neighbors=Collections.emptyList();
        private int pressed=-1;
        Graph(Context context){super(context);setClickable(true);setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);targets.clear();if(selected<0)return;
            float w=getWidth(),h=getHeight(),box=w*.36f, left=dp(4),right=w-box-dp(4);
            float cy=h/2f;
            for(int i=0;i<neighbors.size();i++){
                int neighbor=neighbors.get(i);float y=(h-dp((neighbors.size()-1)*90))/2f+dp(i*90);
                boolean outward=false,inward=false;
                for(AreaConnections.Edge e:history.edges){
                    outward|=e.fromArea==selected&&e.toArea==neighbor;
                    inward|=e.fromArea==neighbor&&e.toArea==selected;
                }
                ink.setColor(Color.BLACK);ink.setStrokeWidth(dp(2));ink.setStyle(Paint.Style.STROKE);
                float x1=left+box,x2=right;
                canvas.drawLine(x1,cy,x2,y,ink);
                if(outward)arrow(canvas,x1,cy,x2,y);
                if(inward)arrow(canvas,x2,y,x1,cy);
                node(canvas,neighbor,new RectF(right,y-dp(28),right+box,y+dp(28)),false);
            }
            node(canvas,selected,new RectF(left,cy-dp(28),left+box,cy+dp(28)),true);
        }
        private void arrow(Canvas c,float x1,float y1,float x2,float y2){
            double a=Math.atan2(y2-y1,x2-x1);float length=dp(10);
            c.drawLine(x2,y2,x2-length*(float)Math.cos(a-.45),y2-length*(float)Math.sin(a-.45),ink);
            c.drawLine(x2,y2,x2-length*(float)Math.cos(a+.45),y2-length*(float)Math.sin(a+.45),ink);
        }
        private void node(Canvas c,int area,RectF box,boolean center){
            ink.setStyle(Paint.Style.FILL);ink.setColor(center?Color.BLACK:Color.WHITE);c.drawRoundRect(box,dp(4),dp(4),ink);
            ink.setStyle(Paint.Style.STROKE);ink.setColor(Color.BLACK);ink.setStrokeWidth(dp(area==here?3:1));c.drawRoundRect(box,dp(4),dp(4),ink);
            ink.setStyle(Paint.Style.FILL);ink.setColor(center?Color.WHITE:Color.BLACK);ink.setTextSize(dp(14));ink.setTextAlign(Paint.Align.CENTER);
            String name=label(area);float available=box.width()-dp(12);
            String first=name,second="";
            if(ink.measureText(name)>available){int split=name.lastIndexOf(' ',name.length()/2+5);if(split>0){first=name.substring(0,split);second=name.substring(split+1);}}
            float scale=Math.min(1,available/Math.max(ink.measureText(first),Math.max(1,ink.measureText(second))));ink.setTextSize(dp(14)*scale);
            c.drawText(first,box.centerX(),box.centerY()-(second.isEmpty()?0:dp(8)),ink);
            if(!second.isEmpty())c.drawText(second,box.centerX(),box.centerY()+dp(8),ink);
            if(area==here){ink.setTextSize(dp(10));c.drawText("You are here",box.centerX(),box.bottom-dp(5),ink);}
            targets.put(area,box);
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){pressed=-1;for(Map.Entry<Integer,RectF> t:targets.entrySet())if(t.getValue().contains(event.getX(),event.getY()))pressed=t.getKey();return true;}
            if(event.getActionMasked()==MotionEvent.ACTION_UP){RectF box=targets.get(pressed);if(box!=null&&box.contains(event.getX(),event.getY())){performClick();choose(pressed);}pressed=-1;return true;}
            if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)pressed=-1;return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);for(int area:neighbors)info.addAction(new AccessibilityNodeInfo.AccessibilityAction(0x04000000+area,"Connections for "+label(area)));}
        @Override public boolean performAccessibilityAction(int action,Bundle args){int area=action-0x04000000;if(neighbors.contains(area)){choose(area);return true;}return super.performAccessibilityAction(action,args);}
    }
}
