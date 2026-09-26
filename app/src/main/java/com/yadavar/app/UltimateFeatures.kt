package com.yadavar.app

import android.Manifest
import android.app.*
import android.content.*
import android.media.MediaRecorder
import android.content.pm.PackageManager
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yadavar.app.core.GeofenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.*

private object UltimateStore {
    private const val PREF="yadavar_ultimate"
    private fun p(c:Context)=c.getSharedPreferences(PREF,0)
    fun text(c:Context,k:String,d:String="")=p(c).getString(k,d).orEmpty()
    fun putText(c:Context,k:String,v:String){p(c).edit().putString(k,v).apply()}
    fun int(c:Context,k:String,d:Int=0)=p(c).getInt(k,d)
    fun putInt(c:Context,k:String,v:Int){p(c).edit().putInt(k,v).apply()}
    fun obj(c:Context,k:String)=runCatching{JSONObject(text(c,k,"{}"))}.getOrDefault(JSONObject())
    fun putObj(c:Context,k:String,o:JSONObject){putText(c,k,o.toString())}
    fun attachments(c:Context,id:Int)=runCatching{val a=JSONArray(text(c,"files_"+id,"[]"));(0 until a.length()).map{a.optString(it)}}.getOrDefault(emptyList())
    fun addFile(c:Context,id:Int,path:String){val a=JSONArray();(attachments(c,id)+path).distinct().forEach{a.put(it)};putText(c,"files_"+id,a.toString())}
    fun removeFile(c:Context,id:Int,path:String){val a=JSONArray();attachments(c,id).filterNot{it==path}.forEach{a.put(it)};putText(c,"files_"+id,a.toString())}
    fun history(c:Context)=runCatching{val a=JSONArray(text(c,"history","[]"));(0 until a.length()).map{a.optString(it)}.reversed()}.getOrDefault(emptyList())
    fun history(c:Context,t:TodoItem,action:String){val a=JSONArray(text(c,"history","[]"));a.put(JSONObject().put("time",System.currentTimeMillis()).put("id",t.id).put("task",t.title).put("action",action).toString());while(a.length()>300)a.remove(0);putText(c,"history",a.toString())}
    fun subtasks(c:Context,id:Int)=runCatching{JSONArray(text(c,"sub_"+id,"[]"))}.getOrDefault(JSONArray())
    fun setSubtasks(c:Context,id:Int,a:JSONArray)=putText(c,"sub_"+id,a.toString())
    fun tags(c:Context)=obj(c,"tags")
    fun tag(c:Context,n:String,h:String){val o=tags(c);o.put(n,h);putObj(c,"tags",o)}
    fun points(c:Context)=int(c,"points")
    fun award(c:Context,t:TodoItem){val s=p(c).getStringSet("awarded",emptySet())!!.toMutableSet();if(s.add(t.id.toString())){putInt(c,"points",points(c)+if(t.priority=="high")30 else 20);p(c).edit().putStringSet("awarded",s).apply()}}
    fun dashboard(c:Context)=text(c,"dashboard","progress,goals,points,timeline,quick").split(",").filter{it.isNotBlank()}
    fun setDashboard(c:Context,l:List<String>){putText(c,"dashboard",l.joinToString(","))}
    fun taskOrder(c:Context):List<Int> = text(c,"task_order","").split(",").mapNotNull{it.toIntOrNull()}
    fun setTaskOrder(c:Context,l:List<Int>){putText(c,"task_order",l.joinToString(","))}
    fun setRepeatRule(c:Context,id:Int,every:Int,unit:String,days:String,end:String){
        val o=obj(c,"repeat_rules")
        o.put(id.toString(),JSONObject().put("every",every).put("unit",unit).put("days",days).put("end",end))
        putObj(c,"repeat_rules",o)
    }
}

private object JalaliDate {
    fun fromJalali(y:Int,m:Int,d:Int):LocalDate?=runCatching{
        require(m in 1..12&&d>=1)
        var days=(y-979)*365+(y-979)/33*8+((y-979)%33+3)/4
        for(x in 1 until m)days+=if(x<=6)31 else 30
        days+=d-1
        var gy=1600+days/365;var rem=days%365
        while(true){val leap=(gy%4==0&&gy%100!=0)||gy%400==0;val n=if(leap)366 else 365;if(rem<n)break;rem-=n;gy++}
        val md=intArrayOf(31,28,31,30,31,30,31,31,30,31,30,31);if((gy%4==0&&gy%100!=0)||gy%400==0)md[1]=29
        var gm=1;while(rem>=md[gm-1]){rem-=md[gm-1];gm++};LocalDate.of(gy,gm,rem+1)
    }.getOrNull()
    fun toJalali(g:LocalDate):String{
        var days=0
        for(y in 1600 until g.year)days+=if((y%4==0&&y%100!=0)||y%400==0)366 else 365
        val md=intArrayOf(31,28,31,30,31,30,31,31,30,31,30,31);if((g.year%4==0&&g.year%100!=0)||g.year%400==0)md[1]=29
        for(m in 1 until g.monthValue)days+=md[m-1]
        days+=g.dayOfYear-1
        var jy=979+days/365;var r=days%365;while(r>=365){jy++;r-=365};var jm=1
        while(jm<=6&&r>=31){r-=31;jm++};while(jm<=11&&r>=30){r-=30;jm++}
        return jy.toString()+"/"+jm+"/"+(r+1)
    }
}

@Composable
fun UltimateFeaturesScreen(context:Context,tasks:List<TodoItem>,onUpdate:(TodoItem)->Unit,onDelete:(Int)->Unit){
    var tab by remember{mutableIntStateOf(0)}
    val tabs=listOf("تکرار","Geofence","پیوست","صدا","تاریخچه","Drag","زیرکار","برچسب","جستجو","Timeline","اهداف","امتیاز","داشبورد","اعلان","آمار","شمسی")
    Column(Modifier.fillMaxSize()){
        Text("امکانات حرفه‌ای",fontSize=23.sp,modifier=Modifier.padding(12.dp))
        ScrollableTabRow(tab,edgePadding=0.dp){tabs.forEachIndexed{i,s->Tab(tab==i,{tab=i},text={Text(s)})}}
        when(tab){
            0->RepeatPanel(context,tasks,onUpdate)
            1->GeoPanel(context,tasks)
            2->FilePanel(context,tasks)
            3->AudioPanel(context,tasks)
            4->HistoryPanel(context)
            5->DragPanel(context,tasks,onUpdate)
            6->SubtaskPanel(context,tasks)
            7->TagPanel(context)
            8->SearchPanel(context,tasks,onUpdate)
            9->TimelinePanel(tasks)
            10->GoalPanel(context,tasks)
            11->PointsPanel(context,tasks)
            12->DashboardPanel(context,tasks)
            13->ChannelPanel(context)
            14->StatsPanel(tasks)
            15->JalaliPanel(context,tasks,onUpdate)
        }
    }
}

@Composable private fun TaskChoice(tasks:List<TodoItem>,id:Int,set:(Int)->Unit){
    var open by remember{mutableStateOf(false)}
    Box{OutlinedButton({open=true},Modifier.fillMaxWidth()){Text(tasks.firstOrNull{it.id==id}?.title?:"انتخاب کار")};DropdownMenu(open,{open=false}){tasks.forEach{DropdownMenuItem(text={Text(it.title)},onClick={set(it.id);open=false})}}}
}

@Composable private fun RepeatPanel(c:Context,tasks:List<TodoItem>,update:(TodoItem)->Unit){
    var id by remember{mutableIntStateOf(tasks.firstOrNull()?.id?:-1)};var every by remember{mutableStateOf("2")};var unit by remember{mutableStateOf("week")};var end by remember{mutableStateOf("")};var weekdays by remember{mutableStateOf("شنبه,دوشنبه,چهارشنبه")}
    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
        Text("تکرارهای پیشرفته",fontSize=20.sp);TaskChoice(tasks,id){id=it}
        OutlinedTextField(every,{every=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),label={Text("هر چند")})
        OutlinedTextField(unit,{unit=it},Modifier.fillMaxWidth(),label={Text("واحد day/week/month/year")})
        OutlinedTextField(weekdays,{weekdays=it},Modifier.fillMaxWidth(),label={Text("روزهای هفته")})
        OutlinedTextField(end,{end=it},Modifier.fillMaxWidth(),label={Text("پایان YYYY-MM-DD")})
        Button({tasks.firstOrNull{it.id==id}?.let{t->val e=every.toIntOrNull()?.coerceAtLeast(1)?:1;UltimateStore.setRepeatRule(c,t.id,e,unit,weekdays,end);UltimateStore.history(c,t,"repeat:"+e+":"+unit+":"+weekdays+":"+end);update(t.copy(repeat="custom",customEvery=e,customUnit=unit))}}){Text("ذخیره تکرار")}
    }
}

@Composable private fun GeoPanel(c:Context,tasks:List<TodoItem>){
    var id by remember{mutableIntStateOf(tasks.firstOrNull()?.id?:-1)};var lat by remember{mutableStateOf("")};var lon by remember{mutableStateOf("")};var radius by remember{mutableStateOf("150")}
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){}
    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
        Text("یادآوری مکانی واقعی",fontSize=20.sp);TaskChoice(tasks,id){id=it}
        OutlinedTextField(lat,{lat=it},Modifier.fillMaxWidth(),label={Text("Latitude")});OutlinedTextField(lon,{lon=it},Modifier.fillMaxWidth(),label={Text("Longitude")});OutlinedTextField(radius,{radius=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),label={Text("شعاع متر")})
        Button({permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}){Text("اجازه مکان")}
        Button({val t=tasks.firstOrNull{it.id==id};val a=lat.toDoubleOrNull();val o=lon.toDoubleOrNull();val r=radius.toFloatOrNull();if(t!=null&&a!=null&&o!=null&&r!=null)GeofenceManager.add(c,GeofenceManager.Item(t.id,t.title,a,o,r))}){Text("فعال‌سازی Geofence")}
        Text("تعداد فعال: "+GeofenceManager.load(c).size)
    }
}

@Composable private fun FilePanel(c:Context,tasks:List<TodoItem>){
    var id by remember{mutableIntStateOf(tasks.firstOrNull()?.id?:-1)};var refresh by remember{mutableIntStateOf(0)}
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)runCatching{val dir=File(c.filesDir,"attachments/"+id).apply{mkdirs()};val out=File(dir,System.currentTimeMillis().toString()+".bin");c.contentResolver.openInputStream(uri)?.use{input->FileOutputStream(out).use{input.copyTo(it)}};UltimateStore.addFile(c,id,out.absolutePath);refresh++}
    }
    Column(Modifier.fillMaxSize().padding(12.dp)){Text("پیوست واقعی",fontSize=20.sp);TaskChoice(tasks,id){id=it};Button({pick.launch(arrayOf("*/*"))}){Text("افزودن فایل")};LazyColumn{items(UltimateStore.attachments(c,id),key={it+refresh.toString()}){p->Row(Modifier.fillMaxWidth().padding(6.dp)){Text(File(p).name,Modifier.weight(1f));IconButton({File(p).delete();UltimateStore.removeFile(c,id,p);refresh++}){Icon(Icons.Default.Delete,null)}}}}}
}

@Composable private fun AudioPanel(c:Context,tasks:List<TodoItem>){
    var id by remember{mutableIntStateOf(tasks.firstOrNull()?.id?:-1)};var recording by remember{mutableStateOf(false)};var rec by remember{mutableStateOf<MediaRecorder?>(null)};var text by remember{mutableStateOf("")}
    val audioPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ok->if(ok){startRecordingForUltimate(c,id){m,path->rec=m;recording=true;UltimateStore.addFile(c,id,path)}}}
    val speech=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){r->text=r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty();UltimateStore.putText(c,"speech_"+id,text)}
    fun start(){val dir=File(c.filesDir,"audio/"+id).apply{mkdirs()};val f=File(dir,System.currentTimeMillis().toString()+".m4a");val m=MediaRecorder();m.setAudioSource(MediaRecorder.AudioSource.MIC);m.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);m.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);m.setOutputFile(f.absolutePath);m.prepare();m.start();rec=m;recording=true;UltimateStore.addFile(c,id,f.absolutePath)}
    fun stop(){runCatching{rec?.stop();rec?.release()};rec=null;recording=false}
    DisposableEffect(Unit){onDispose{runCatching{rec?.stop();rec?.release()}}}
    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text("ضبط و گفتار به متن",fontSize=20.sp);TaskChoice(tasks,id){id=it;text=UltimateStore.text(c,"speech_"+it)};Button({if(recording)stop()else if(androidx.core.content.ContextCompat.checkSelfPermission(c,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)start()else audioPermission.launch(Manifest.permission.RECORD_AUDIO)}){Text(if(recording)"توقف ضبط" else "ضبط صدا")};Button({speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"fa-IR").putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))}){Text("گفتار به متن")};OutlinedTextField(text,{text=it;UltimateStore.putText(c,"speech_"+id,it)},Modifier.fillMaxWidth(),minLines=3,label={Text("متن کار")})}
}

@Composable private fun HistoryPanel(c:Context){
    var refresh by remember{mutableIntStateOf(0)}
    Column(Modifier.fillMaxSize().padding(12.dp)){Text("تاریخچه کامل تغییرات",fontSize=20.sp);Button({refresh++}){Text("به‌روزرسانی")};LazyColumn{items(UltimateStore.history(c),key={it+refresh.toString()}){raw->val o=runCatching{JSONObject(raw)}.getOrNull();if(o!=null)Card(Modifier.fillMaxWidth().padding(3.dp)){Column(Modifier.padding(8.dp)){Text(o.optString("action"));Text("کار: "+o.optString("task"));Text(Date(o.optLong("time")).toString(),fontSize=11.sp)}}}}}}
}

@Composable private fun DragPanel(c:Context,tasks:List<TodoItem>,update:(TodoItem)->Unit){
    var order by remember{mutableStateOf(tasks.toMutableList())};var drag by remember{mutableIntStateOf(-1)}
    Column(Modifier.fillMaxSize().padding(12.dp)){Text("Drag & Drop واقعی",fontSize=20.sp);LazyColumn{itemsIndexed(order,key={_,t->t.id}){i,t->Card(Modifier.fillMaxWidth().padding(3.dp).pointerInput(order){detectDragGesturesAfterLongPress(onDragStart={drag=i},onDragEnd={drag=-1},onDragCancel={drag=-1}){_,dy->if(kotlin.math.abs(dy.y)>25&&drag>=0){val n=(drag+if(dy.y>0)1 else -1).coerceIn(0,order.lastIndex);if(n!=drag){val m=order.toMutableList();val x=m.removeAt(drag);m.add(n,x);order=m;drag=n}}}}){Row(Modifier.padding(12.dp)){Icon(Icons.Default.DragHandle,null);Text(t.title,Modifier.weight(1f));Text((i+1).toString())}}}};Button({UltimateStore.setTaskOrder(c,order.map{it.id});order.forEachIndexed{i,t->update(t);UltimateStore.history(c,t,"order:"+i)}}){Text("ذخیره ترتیب")}}
}

@Composable private fun SubtaskPanel(c:Context,tasks:List<TodoItem>){
    var id by remember{mutableIntStateOf(tasks.firstOrNull()?.id?:-1)};var title by remember{mutableStateOf("")};var parent by remember{mutableStateOf("-1")};var refresh by remember{mutableIntStateOf(0)}
    Column(Modifier.fillMaxSize().padding(12.dp)){Text("زیرکارهای چندسطحی",fontSize=20.sp);TaskChoice(tasks,id){id=it};OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("عنوان")});OutlinedTextField(parent,{parent=it},Modifier.fillMaxWidth(),label={Text("والد ID")});Button({if(title.isNotBlank()){val a=UltimateStore.subtasks(c,id);val n=(0 until a.length()).map{a.optJSONObject(it)?.optInt("id",0)?:0}.maxOrNull()?.plus(1)?:1;a.put(JSONObject().put("id",n).put("title",title).put("parent",parent.toIntOrNull()?:-1));UltimateStore.setSubtasks(c,id,a);title="";refresh++}}){Text("افزودن")};val tree=UltimateStore.subtasks(c,id)
        fun flatten(parent:Int,depth:Int):List<Pair<JSONObject,Int>>{
            val out=mutableListOf<Pair<JSONObject,Int>>()
            for(i in 0 until tree.length()){
                val o=tree.getJSONObject(i)
                if(o.optInt("parent",-1)==parent){out.add(o to depth);out.addAll(flatten(o.optInt("id",-1),depth+1))}
            }
            return out
        }
        LazyColumn{items(flatten(-1,0),key={it.first.optInt("id")}){pair->
            Card(Modifier.fillMaxWidth().padding(start=(pair.second*18).dp,end=3.dp,top=3.dp)){
                Row(Modifier.padding(9.dp),verticalAlignment=Alignment.CenterVertically){
                    Text(pair.first.optString("title"),Modifier.weight(1f))
                    Text("ID "+pair.first.optInt("id"))
                }
            }
        }}
    }
}

@Composable private fun TagPanel(c:Context){
    var n by remember{mutableStateOf("")};var h by remember{mutableStateOf("#6750A4")};var refresh by remember{mutableIntStateOf(0)}
    Column(Modifier.fillMaxSize().padding(12.dp)){Text("برچسب‌های رنگی مستقل",fontSize=20.sp);Row{OutlinedTextField(n,{n=it},Modifier.weight(1f),label={Text("نام")});OutlinedTextField(h,{h=it},Modifier.weight(1f),label={Text("#RRGGBB")});Button({if(n.isNotBlank()){UltimateStore.tag(c,n,h);n="";refresh++}}){Text("+")}};LazyColumn{items(UltimateStore.tags(c).keys().asSequence().toList(),key={it+refresh.toString()}){k->val v=UltimateStore.tags(c).optString(k);Row(Modifier.fillMaxWidth().padding(6.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(22.dp).background(runCatching{Color(android.graphics.Color.parseColor(v))}.getOrDefault(Color.Gray)));Text(k,Modifier.weight(1f));Text(v)}}}}
}

@Composable private fun SearchPanel(c:Context,tasks:List<TodoItem>,update:(TodoItem)->Unit){
    var q by remember{mutableStateOf("")};var open by remember{mutableStateOf(false)};var pri by remember{mutableStateOf("all")}
    val r=tasks.filter{(!open||!it.done)&&(pri=="all"||it.priority==pri)&&(q.isBlank()||scoreSearch(q,it)>35)}.sortedByDescending{scoreSearch(q,it)}
    Column(Modifier.fillMaxSize().padding(12.dp)){Text("جستجوی حرفه‌ای",fontSize=20.sp);OutlinedTextField(q,{q=it},Modifier.fillMaxWidth(),label={Text("فuzzy search در عنوان/یادداشت/برچسب/مکان")});Row{FilterChip(open,{open=it},label={Text("فقط باز")});listOf("all","high","normal","low").forEach{v->FilterChip(pri==v,{pri=v},label={Text(v)})}};LazyColumn{items(r,key={it.id}){t->Card(Modifier.fillMaxWidth().padding(3.dp)){Row(Modifier.padding(8.dp)){Checkbox(t.done,{UltimateStore.history(c,t,"search toggle");update(t.copy(done=!t.done))});Text(t.title)}}}}}
}

@Composable private fun TimelinePanel(tasks:List<TodoItem>){
    var day by remember{mutableStateOf(LocalDate.now())}
    Column(Modifier.fillMaxSize().padding(12.dp)){Text("Timeline تعاملی",fontSize=20.sp);Row(Modifier.horizontalScroll(rememberScrollState())){(-7..14).forEach{n->val d=LocalDate.now().plusDays(n.toLong());FilterChip(day==d,{day=d},label={Text(JalaliDate.toJalali(d).substringAfterLast("/"))})}};LazyColumn{items(tasks.filter{it.dueDate==day.toString()||it.startDate==day.toString()},key={it.id}){t->Card(Modifier.fillMaxWidth().padding(3.dp)){Column(Modifier.padding(10.dp)){Text(t.title);Text(t.startDate+" -> "+t.dueDate)}}}}}
}

@Composable private fun GoalPanel(c:Context,tasks:List<TodoItem>){
    val o=UltimateStore.obj(c,"goals");var daily by remember{mutableStateOf(o.optInt("daily",3).toString())};var monthly by remember{mutableStateOf(o.optInt("monthly",50).toString())}
    val d=LocalDate.now();val today=tasks.count{it.done&&it.dueDate==d.toString()};val month=tasks.count{it.done&&it.dueDate.startsWith(d.year.toString()+"-"+d.monthValue.toString().padStart(2,'0'))}
    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text("اهداف روزانه/ماهانه واقعی",fontSize=20.sp);OutlinedTextField(daily,{daily=it},Modifier.fillMaxWidth(),label={Text("هدف روزانه")});Text("پیشرفت امروز: "+today+" / "+daily);LinearProgressIndicator({(today.toFloat()/(daily.toIntOrNull()?.coerceAtLeast(1)?:1)).coerceIn(0f,1f)},Modifier.fillMaxWidth());OutlinedTextField(monthly,{monthly=it},Modifier.fillMaxWidth(),label={Text("هدف ماهانه")});Text("پیشرفت ماه: "+month+" / "+monthly);Button({o.put("daily",daily.toIntOrNull()?:3);o.put("monthly",monthly.toIntOrNull()?:50);UltimateStore.putObj(c,"goals",o)}){Text("ذخیره اهداف")}}
}

@Composable private fun PointsPanel(c:Context,tasks:List<TodoItem>){
    tasks.filter{it.done}.forEach{UltimateStore.award(c,it)}
    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text("امتیازدهی خودکار",fontSize=20.sp);Text("امتیاز: "+UltimateStore.points(c),fontSize=32.sp);Text("امتیاز بر اساس تکمیل واقعی کار و اولویت ثبت می‌شود.")}
}

@Composable private fun DashboardPanel(c:Context,tasks:List<TodoItem>){
    var order by remember{mutableStateOf(UltimateStore.dashboard(c))};var refresh by remember{mutableIntStateOf(0)}
    val labels=mapOf("progress" to "پیشرفت","goals" to "اهداف","points" to "امتیاز","timeline" to "Timeline","quick" to "دسترسی سریع")
    Column(Modifier.fillMaxSize().padding(12.dp)){Text("داشبورد قابل چیدمان و متصل",fontSize=20.sp);order.forEachIndexed{i,k->Row(Modifier.fillMaxWidth()){Text(labels[k]?:k,Modifier.weight(1f));IconButton({if(i>0){val m=order.toMutableList();val x=m.removeAt(i);m.add(i-1,x);order=m}}){Icon(Icons.Default.KeyboardArrowUp,null)};IconButton({if(i<order.lastIndex){val m=order.toMutableList();val x=m.removeAt(i);m.add(i+1,x);order=m}}){Icon(Icons.Default.KeyboardArrowDown,null)}}};Button({UltimateStore.setDashboard(c,order);refresh++}){Text("ذخیره چیدمان")};HomeDashboard(c,tasks)}
}

@Composable fun HomeDashboard(c:Context,tasks:List<TodoItem>){
    val d=LocalDate.now();val done=tasks.count{it.done};val today=tasks.count{it.done&&it.dueDate==d.toString()}
    UltimateStore.dashboard(c).forEach{when(it){"progress"->Card(Modifier.fillMaxWidth().padding(2.dp)){Text("پیشرفت: "+done+" از "+tasks.size,Modifier.padding(8.dp))};"goals"->Card(Modifier.fillMaxWidth().padding(2.dp)){Text("هدف امروز: "+today,Modifier.padding(8.dp))};"points"->Card(Modifier.fillMaxWidth().padding(2.dp)){Text("امتیاز: "+UltimateStore.points(c),Modifier.padding(8.dp))};"timeline"->Card(Modifier.fillMaxWidth().padding(2.dp)){Text("کارهای امروز: "+tasks.count{it.dueDate==d.toString()},Modifier.padding(8.dp))};"quick"->Card(Modifier.fillMaxWidth().padding(2.dp)){Text("دسترسی سریع آماده است",Modifier.padding(8.dp))}}}
}

@Composable private fun ChannelPanel(c:Context){
    val nm=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    var refresh by remember{mutableIntStateOf(0)}
    Column(Modifier.fillMaxSize().padding(12.dp)){Text("مدیریت کانال‌های اعلان",fontSize=20.sp);Button({listOf("tasks" to "کارها","birthdays" to "تولدها","location_reminders" to "مکان","focus" to "تمرکز").forEach{nm.createNotificationChannel(NotificationChannel(it.first,it.second,NotificationManager.IMPORTANCE_HIGH))};refresh++}){Text("ایجاد همه کانال‌ها")};nm.notificationChannels.forEach{ch->Card(Modifier.fillMaxWidth().padding(3.dp)){Row(Modifier.padding(8.dp)){Text(ch.name,Modifier.weight(1f));TextButton({c.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,c.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,ch.id))}){Text("تنظیم")}}}}}
}

@Composable private fun StatsPanel(tasks:List<TodoItem>){
    val total=tasks.size;val done=tasks.count{it.done};val open=total-done;val over=tasks.count{!it.done&&it.dueDate.isNotBlank()&&it.dueDate<LocalDate.now().toString()}
    val week=(0..6).reversed().map{n->val d=LocalDate.now().minusDays(n.toLong());d to tasks.count{it.done&&it.dueDate==d.toString()}}
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)){item{Text("آمار و نمودار حرفه‌ای",fontSize=20.sp);Text("کل: "+total);Text("انجام: "+done);Text("باز: "+open);Text("عقب‌افتاده: "+over);Text("نرخ تکمیل: "+if(total==0)0 else done*100/total+"٪")}
        item{val barColor=MaterialTheme.colorScheme.primary;Canvas(Modifier.fillMaxWidth().height(180.dp).padding(8.dp)){val max=week.maxOfOrNull{it.second}?:0;if(max>0){val w=size.width/week.size;week.forEachIndexed{i,p->{val h=size.height*(p.second.toFloat()/max.toFloat());drawRect(barColor,topLeft=androidx.compose.ui.geometry.Offset(i*w,size.height-h),size=androidx.compose.ui.geometry.Size(w*0.65f,h))}}}}}
        items(week){p->Row(Modifier.fillMaxWidth().padding(4.dp)){Text(JalaliDate.toJalali(p.first),Modifier.width(90.dp));Text("انجام‌شده: "+p.second)}}}
}

@Composable private fun JalaliPanel(c:Context,tasks:List<TodoItem>,update:(TodoItem)->Unit){
    var id by remember{mutableIntStateOf(tasks.firstOrNull()?.id?:-1)};var start by remember{mutableStateOf("")};var due by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
    LaunchedEffect(id){tasks.firstOrNull{it.id==id}?.let{start=it.startDate.takeIf(String::isNotBlank)?.let{d->JalaliDate.toJalali(LocalDate.parse(d))}.orEmpty();due=it.dueDate.takeIf(String::isNotBlank)?.let{d->JalaliDate.toJalali(LocalDate.parse(d))}.orEmpty()}}
    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text("تقویم شمسی متصل به تاریخ کار",fontSize=20.sp);TaskChoice(tasks,id){id=it};OutlinedTextField(start,{start=it},Modifier.fillMaxWidth(),label={Text("شروع YYYY/MM/DD شمسی")});OutlinedTextField(due,{due=it},Modifier.fillMaxWidth(),label={Text("سررسید YYYY/MM/DD شمسی")});if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);Button({val t=tasks.firstOrNull{it.id==id};val s=parseJalali(start);val d=parseJalali(due);if(t!=null&&(start.isBlank()||s!=null)&&(due.isBlank()||d!=null)){update(t.copy(startDate=s?.toString().orEmpty(),dueDate=(d?:s)?.toString().orEmpty()));UltimateStore.history(c,t,"jalali dates")}else error="تاریخ شمسی نامعتبر است"}){Text("ذخیره")};Text("امروز: "+JalaliDate.toJalali(LocalDate.now()))}
}

private fun startRecordingForUltimate(c:Context,id:Int,onReady:(MediaRecorder,String)->Unit){
    val dir=File(c.filesDir,"audio/"+id).apply{mkdirs()}
    val f=File(dir,System.currentTimeMillis().toString()+".m4a")
    val m=MediaRecorder()
    m.setAudioSource(MediaRecorder.AudioSource.MIC);m.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);m.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);m.setOutputFile(f.absolutePath);m.prepare();m.start();onReady(m,f.absolutePath)
}
private fun parseJalali(s:String):LocalDate?{val x=s.trim().replace('-','/').split('/');if(x.size!=3)return null;return JalaliDate.fromJalali(x[0].toIntOrNull()?:return null,x[1].toIntOrNull()?:return null,x[2].toIntOrNull()?:return null)}
private fun scoreSearch(q:String,t:TodoItem):Int{if(q.isBlank())return 100;val all=(t.title+" "+t.note+" "+t.tags+" "+t.category+" "+t.location).lowercase();val x=q.lowercase();if(all.contains(x))return 100;return all.split(Regex("\\s+")).maxOfOrNull{100-lev(x,it).coerceAtMost(100)}?.takeIf{it>35}?:0}
private fun lev(a:String,b:String):Int{val d=Array(a.length+1){IntArray(b.length+1)};for(i in d.indices)d[i][0]=i;for(j in d[0].indices)d[0][j]=j;for(i in 1..a.length)for(j in 1..b.length)d[i][j]=minOf(d[i-1][j]+1,d[i][j-1]+1,d[i-1][j-1]+if(a[i-1]==b[j-1])0 else 1);return d[a.length][b.length]}
