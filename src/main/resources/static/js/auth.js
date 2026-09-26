function v(id){ return document.getElementById(id).value; }

function setMsg(id, ok, text){
  const el = document.getElementById(id);
  el.className = 'msg show ' + (ok ? 'ok' : 'err');
  el.textContent = text;
}

async function callAuthApi(path, body){
  try{
    const res = await fetch(path, {
      method: 'POST',
      headers: {'Content-Type':'application/json'},
      body: JSON.stringify(body)
    });
    let data; try{ data = await res.json(); } catch(e){ data = {}; }
    return {ok: res.ok, status: res.status, data};
  } catch(e){
    return {ok:false, status:0, data:{message:'Could not reach the server. Is it running?'}};
  }
}
