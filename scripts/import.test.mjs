// 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {readBatch} from "../frontend/batch-import.mjs";
const cat=JSON.parse(readFileSync(new URL("../frontend/catalog.json",import.meta.url),"utf8"));
const sample={title:"实测导入",settings:cat.settings,rows:cat.rows};
test("本工程样本可无损导入，接受 UTF-8 BOM",()=>assert.deepEqual(readBatch("\uFEFF"+JSON.stringify(sample),cat),sample));
test("未知参数或列不可静默丢弃",()=>{
  assert.throws(()=>readBatch(JSON.stringify({...sample,settings:{...sample.settings,unknown:"x"}}),cat),/参数.*未知字段 unknown/);
  assert.throws(()=>readBatch(JSON.stringify({...sample,rows:[cat.rows[0],{...cat.rows[0],unexpected:1}]}),cat),/第 2 行.*未知字段 unexpected/);
});
test("错误行和类型不可被转换成另一条业务记录",()=>{
  assert.throws(()=>readBatch(JSON.stringify({...sample,rows:[{...cat.rows[0],id:null}]}),cat),/第 1 行 id/);
  assert.throws(()=>readBatch(JSON.stringify({...sample,rows:[{}]}),cat),/第 1 行.*缺少/);
  assert.throws(()=>readBatch(JSON.stringify({...sample,rows:[]}),cat),/1–200/);
  assert.throws(()=>readBatch("{",cat),/无法解析/);
});
