#!/usr/bin/env python3
"""上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
Launch only a disposable instance with private data and verify the packaged delivery.
商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
"""
import json, os, secrets, socket, subprocess, tempfile, time, sys
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

ROOT = Path(__file__).resolve().parents[1]
def smoke():
    jar = ROOT / "backend/target/app.jar"
    if not jar.is_file():
        raise SystemExit("Build first: ./mvnw -f backend/pom.xml package (Windows: mvnw.cmd -f backend/pom.xml package)")
    with tempfile.TemporaryDirectory(prefix="zh-workspace-") as tmp:
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        base = "http://127.0.0.1:" + str(port)
        password = secrets.token_urlsafe(24)
        # Inherit only the runtime, never Spring/JDBC overrides or production JVM options.
        runtime={k:v for k,v in os.environ.items() if k.upper() in {
            "JAVA_HOME","PATH","HOME","USERPROFILE","SYSTEMROOT","WINDIR","COMSPEC","PATHEXT",
            "TMPDIR","TEMP","TMP","LANG","LC_ALL","LC_CTYPE","TZ"}}
        env = dict(runtime, SPRING_CONFIG_LOCATION="classpath:/application.properties",
                   PORT=str(port), BIND_ADDRESS="127.0.0.1", ADMIN_PASSWORD=password,
                   DB_USER="sa", DB_PASSWORD="", DB_URL="jdbc:h2:file:"+str(Path(tmp)/"db")+";MODE=MySQL;DATABASE_TO_LOWER=TRUE")
        def call(path, method="GET", data=None, token=None, key=None):
            headers = {"Content-Type":"application/json"}
            if token: headers["Authorization"]="Bearer "+token
            if key: headers["Idempotency-Key"]=key
            raw = json.dumps(data,ensure_ascii=False).encode() if data is not None else None
            with urlopen(Request(base+path,data=raw,method=method,headers=headers),timeout=10) as res:
                v=res.read()
                return json.loads(v) if v else None
        def boot():
            command = ["java","-jar",str(jar)] if os.name=="nt" else ["bash",str(ROOT/"scripts/start.sh"),"--no-build"]
            process=subprocess.Popen(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
            try:
                for _ in range(200):
                    if process.poll() is not None: raise RuntimeError("startup failed")
                    try:
                        call("/api/health"); return process
                    except (URLError,TimeoutError):time.sleep(.15)
                raise RuntimeError("startup timeout")
            except BaseException:
                stop(process); raise
        def stop(p):
            if p is not None and p.poll() is None:
                p.terminate()
                try:p.wait(timeout=12)
                except subprocess.TimeoutExpired:p.kill();p.wait()
        with (Path(tmp)/"server.log").open("w+") as log:
            p=None
            try:
                p=boot()
                subprocess.run([sys.executable,str(ROOT/'scripts/check-service.py')],cwd=ROOT,env=env,check=True,timeout=10)
                for path in ("/","/app.js","/style.css"):
                    with urlopen(base+path,timeout=5) as res:assert res.status==200 and res.read()
                cat=call("/catalog.json")
                batch={"title":"离线启动自检","settings":cat["settings"],"rows":cat["rows"]}
                auth=lambda:call("/api/login","POST",{"username":"admin","password":password})["token"]
                token=auth();one=call("/api/runs","POST",batch,token,"startup-test")
                assert one["result"]["rows"] and call("/api/runs",token=token)["total"]==1
                assert call("/api/runs","POST",batch,token,"startup-test")["id"]==one["id"]
                stop(p);env.pop("ADMIN_PASSWORD");p=boot()
                again=call("/api/runs/"+one["id"],token=auth());assert one["result"]==again["result"]
                closed=call("/api/runs/"+one["id"]+"/withdraw","POST",{"version":again["revision"],"note":"自检完成，保留记录"},auth())
                assert closed["stage"]=="WITHDRAWN" and closed["result"]==one["result"]
                assert call("/api/runs?state=WITHDRAWN&scope=mine",token=auth())["total"]==1
                fixture=json.loads((ROOT/"examples/acceptance.json").read_text(encoding="utf8"))
                result=call("/api/runs","POST",fixture,auth(),"domain-acceptance")["result"]
                expected=json.loads("{\"needsReview\":1}")
                assert all(k in result["metrics"] and result["metrics"][k]==v for k,v in expected.items()),result["metrics"]
                evidence=json.loads("{\"id\":\"I\",\"initialReason\":\"TIE\"}")
                assert any(all(k in row and row[k]==v for k,v in evidence.items()) for row in result["rows"]),result["rows"]
                print("PASS: packaged startup, login, execution, idempotency, restart, withdrawal and project-specific acceptance")
            except BaseException:
                log.flush();log.seek(0);print(log.read()[-3500:],file=sys.stderr);raise
            finally:stop(p)
if __name__=="__main__":smoke()
