/* ==========================================================================
   TECHFIX — camada de apresentação.
   Consome EXATAMENTE os endpoints que ja existiam no projeto:
     GET  /api/usuarios
     POST /api/usuarios                {nome, email}
     GET  /api/usuarios/{id}/tickets
     POST /api/tickets                 {usuarioId, texto}
     GET  /api/tickets/{id}
     POST /api/tickets/{id}/foto       multipart, campo "imagem"
     GET  /api/metricas
   Nenhuma rota nova, nenhum campo novo: so a forma de mostrar mudou.
   ========================================================================== */
(function (global) {
  'use strict';

  /* ---------------------------------------------------------- utilidades */
  const $  = (s, ctx) => (ctx || document).querySelector(s);
  const $$ = (s, ctx) => Array.from((ctx || document).querySelectorAll(s));

  function escapar(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  /** #12 -> #TF-0012. E so formatacao do id real, nada e inventado. */
  function protocolo(id) {
    return '#TF-' + String(id).padStart(4, '0');
  }

  function quando(iso) {
    if (!iso) return '';
    const d = new Date(iso), s = (Date.now() - d.getTime()) / 1000;
    if (s < 60)    return 'agora há pouco';
    if (s < 3600)  return 'há ' + Math.floor(s / 60) + ' min';
    if (s < 86400) return 'há ' + Math.floor(s / 3600) + ' h';
    return d.toLocaleDateString('pt-BR');
  }

  function nAnimado(el, alvo, casas, sufixo) {
    const dur = 620, t0 = performance.now();
    (function passo(t) {
      const p = Math.min(1, (t - t0) / dur);
      const v = alvo * (1 - Math.pow(1 - p, 3));
      el.textContent = v.toFixed(casas || 0) + (sufixo || '');
      if (p < 1) requestAnimationFrame(passo);
    })(t0);
  }

  /* ---------------------------------------------------------------- tema */
  const ICONE_SOL = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>';
  const ICONE_LUA = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>';

  function aplicarTema(t) {
    document.documentElement.dataset.tema = t;
    $$('[data-acao="tema"]').forEach(b => { b.innerHTML = t === 'escuro' ? ICONE_SOL : ICONE_LUA; });
    try { localStorage.setItem('techfix-tema', t); } catch (e) {}
  }
  function iniciarTema() {
    let t = 'claro';
    try { t = localStorage.getItem('techfix-tema') || 'claro'; } catch (e) {}
    aplicarTema(t);
    document.addEventListener('click', ev => {
      const b = ev.target.closest('[data-acao="tema"]');
      if (b) aplicarTema(document.documentElement.dataset.tema === 'escuro' ? 'claro' : 'escuro');
    });
  }

  /* -------------------------------------------------------------- avisos */
  const IC_OK  = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg>';
  const IC_ERR = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 8v5M12 16.5v.01"/></svg>';

  function avisar(msg, erro) {
    let caixa = $('#avisos');
    if (!caixa) { caixa = document.createElement('div'); caixa.id = 'avisos'; document.body.appendChild(caixa); }
    const d = document.createElement('div');
    d.className = 'aviso' + (erro ? ' erro' : '');
    d.innerHTML = (erro ? IC_ERR : IC_OK) + '<span>' + escapar(msg) + '</span>';
    caixa.appendChild(d);
    setTimeout(() => {
      d.style.transition = 'opacity .3s,transform .3s';
      d.style.opacity = '0'; d.style.transform = 'translateY(8px)';
      setTimeout(() => d.remove(), 320);
    }, 4600);
  }

  /* ----------------------------------------------------------------- api */
  async function api(url, method, body, form) {
    const opt = { method: method || 'GET' };
    if (form) opt.body = form;
    else if (body) { opt.headers = { 'Content-Type': 'application/json' }; opt.body = JSON.stringify(body); }
    const r = await fetch(url, opt);
    const bruto = await r.text();
    let j = null;
    try { j = bruto ? JSON.parse(bruto) : null; } catch (e) {}
    if (!r.ok) {
      const e = new Error((j && (j.detail || j.title)) || ('Falha na requisição (HTTP ' + r.status + ')'));
      e.status = r.status; e.corpo = j;
      throw e;
    }
    return j;
  }

  /** Cadastra o cliente; se o e-mail ja existir (409), reaproveita o existente. */
  async function garantirCliente(nome, email) {
    try {
      return await api('/api/usuarios', 'POST', { nome: nome, email: email });
    } catch (e) {
      if (e.status !== 409) throw e;
      const lista = await api('/api/usuarios');
      const achado = lista.find(u => (u.email || '').toLowerCase() === email.toLowerCase());
      if (achado) return achado;
      throw e;
    }
  }

  /* ------------------------------------------------- leitura dos tickets */
  /** Situacao derivada SOMENTE de campos reais do ticket. Nada e inventado. */
  function situacao(t) {
    if (t.origem === 'FALLBACK') return { txt: 'Aguardando triagem técnica', cor: 'var(--fallback)' };
    if (t.descricaoAnexo)        return { txt: 'Triado com imagem', cor: 'var(--ia)' };
    if (t.categoriaIa)           return { txt: 'Triado pela IA',    cor: 'var(--ok)' };
    return { txt: 'Registrado', cor: 'var(--baixa)' };
  }
  function concorda(t) {
    return !!(t.categoriaIa && t.categoriaRegras &&
      t.categoriaIa.toLowerCase() === t.categoriaRegras.toLowerCase());
  }

  /* -------------------------------------------------- revelar ao rolar */
  function revelar() {
    const alvos = $$('.rev');
    if (!alvos.length) return;
    if (!('IntersectionObserver' in window)) { alvos.forEach(e => e.classList.add('vis')); return; }
    const obs = new IntersectionObserver(ent => {
      ent.forEach(e => { if (e.isIntersecting) { e.target.classList.add('vis'); obs.unobserve(e.target); } });
    }, { threshold: .12, rootMargin: '0px 0px -40px' });
    alvos.forEach((e, i) => { e.style.transitionDelay = Math.min(i % 6, 5) * 70 + 'ms'; obs.observe(e); });
  }

  /* ------------------------------------------------------ chat TECHFIX AI */
  const SUGESTOES = [
    'Meu celular não liga',
    'Meu notebook está lento',
    'Meu computador está desligando sozinho',
    'Minha TV está sem imagem'
  ];

  function montarChat() {
    if ($('#chatCx')) return;

    const bt = document.createElement('button');
    bt.className = 'chat-bt'; bt.id = 'chatBt'; bt.type = 'button';
    bt.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M12 3l1.9 4.8L18.7 9.7l-4.8 1.9L12 16.4l-1.9-4.8L5.3 9.7l4.8-1.9z"/><path d="M19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8z"/></svg> TECHFIX AI';

    const cx = document.createElement('div');
    cx.className = 'chat-cx'; cx.id = 'chatCx';
    cx.innerHTML =
      '<div class="chat-top">' +
        '<span class="av"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M12 3l1.9 4.8L18.7 9.7l-4.8 1.9L12 16.4l-1.9-4.8L5.3 9.7l4.8-1.9z"/></svg></span>' +
        '<span><b>TECHFIX AI</b><small>Triagem inteligente · responde em segundos</small></span>' +
        '<button class="chat-fechar" id="chatFechar" aria-label="Fechar">' +
          '<svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="M18 6L6 18M6 6l12 12"/></svg>' +
        '</button>' +
      '</div>' +
      '<div class="chat-corpo" id="chatCorpo"></div>' +
      '<div class="chat-sugestoes" id="chatSug"></div>' +
      '<form class="chat-pe" id="chatForm">' +
        '<input id="chatTxt" placeholder="Descreva o problema..." autocomplete="off">' +
        '<button type="submit" aria-label="Enviar"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4z"/></svg></button>' +
      '</form>';

    document.body.appendChild(bt);
    document.body.appendChild(cx);

    const corpo = $('#chatCorpo');
    function fala(texto, quem, classe) {
      const b = document.createElement('div');
      b.className = 'bolha ' + (classe || quem);
      b.textContent = texto;
      corpo.appendChild(b);
      corpo.scrollTop = corpo.scrollHeight;
      return b;
    }

    fala('Olá! Sou a inteligência artificial da TECHFIX. Descreva o problema do seu equipamento que eu faço a triagem e já abro o seu chamado.', 'ia');

    $('#chatSug').innerHTML = SUGESTOES.map(s => '<button type="button" class="sug">' + escapar(s) + '</button>').join('');
    $('#chatSug').addEventListener('click', ev => {
      const s = ev.target.closest('.sug');
      if (!s) return;
      $('#chatTxt').value = s.textContent;
      $('#chatForm').requestSubmit();
    });

    function abrirChat(v) {
      cx.classList.toggle('aberto', v);
      bt.style.display = v ? 'none' : '';
      if (v) setTimeout(() => $('#chatTxt').focus(), 60);
    }
    bt.onclick = () => abrirChat(true);
    $('#chatFechar').onclick = () => abrirChat(false);
    document.addEventListener('keydown', e => { if (e.key === 'Escape' && cx.classList.contains('aberto')) abrirChat(false); });

    $('#chatForm').addEventListener('submit', async ev => {
      ev.preventDefault();
      const txt = $('#chatTxt').value.trim();
      if (!txt) return;
      $('#chatTxt').value = '';
      fala(txt, 'eu');

      const pensando = document.createElement('div');
      pensando.className = 'bolha ia';
      pensando.innerHTML = '<span class="pontos" style="color:var(--marca)"><i></i><i></i><i></i></span>';
      corpo.appendChild(pensando);
      corpo.scrollTop = corpo.scrollHeight;

      try {
        const cliente = await garantirCliente('Visitante do site', 'visitante@techfix.com.br');
        const t = await api('/api/tickets', 'POST', { usuarioId: cliente.id, texto: txt });
        pensando.remove();
        fala(t.resposta || 'Chamado registrado.', 'ia');
        fala('Chamado ' + protocolo(t.id) + ' · categoria ' + (t.categoriaIa || t.categoriaRegras) +
             ' · prioridade ' + t.prioridade + ' · resposta em ' + t.tempoMs + ' ms (' + t.origem + ')',
             'ia', 'meta');
        global.TECHFIX.aoAbrirChamado && global.TECHFIX.aoAbrirChamado(t);
      } catch (e) {
        pensando.remove();
        fala('Não consegui concluir agora: ' + e.message, 'ia');
      }
    });
  }

  /* ------------------------------------------------------------- arranque */
  function iniciar(opcoes) {
    const o = opcoes || {};
    iniciarTema();
    revelar();
    if (o.chat !== false) montarChat();
  }

  global.TECHFIX = {
    $: $, $$: $$, api: api, avisar: avisar, escapar: escapar,
    protocolo: protocolo, quando: quando, nAnimado: nAnimado,
    garantirCliente: garantirCliente, situacao: situacao, concorda: concorda,
    aplicarTema: aplicarTema, revelar: revelar, iniciar: iniciar,
    aoAbrirChamado: null
  };
})(window);
