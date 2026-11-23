'use strict';

(function () {
  // "Salin kode" buttons on license cards.
  document.querySelectorAll('.copy-btn').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var text = btn.getAttribute('data-copy');
      var done = function () {
        var old = btn.textContent;
        btn.textContent = 'Tersalin ✓';
        setTimeout(function () { btn.textContent = old; }, 1500);
      };
      if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(done);
      } else {
        var ta = document.createElement('textarea');
        ta.value = text; document.body.appendChild(ta); ta.select();
        document.execCommand('copy'); ta.remove(); done();
      }
    });
  });

  // "Cek File" tool: hash locally with Web Crypto, send only the hash.
  var fileInput = document.getElementById('scan-file');
  var hashInput = document.getElementById('scan-hash');
  var btn = document.getElementById('scan-btn');
  var out = document.getElementById('scan-result');
  if (!btn) return;

  var MAX_BYTES = 200 * 1024 * 1024;

  function show(kind, html) {
    out.hidden = false;
    out.className = 'scan-result scan-' + kind;
    out.innerHTML = html;
  }
  function esc(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function toHex(buf) {
    return Array.prototype.map.call(new Uint8Array(buf), function (b) {
      return b.toString(16).padStart(2, '0');
    }).join('');
  }

  fileInput.addEventListener('change', function () { if (fileInput.files.length) hashInput.value = ''; });
  hashInput.addEventListener('input', function () { if (hashInput.value) fileInput.value = ''; });

  btn.addEventListener('click', async function () {
    var file = fileInput.files[0];
    var sha256 = hashInput.value.trim().toLowerCase();
    var label = '';

    if (!file && !sha256) return show('info', 'Pilih file atau tempel hash terlebih dahulu.');

    btn.disabled = true;
    try {
      if (file) {
        if (file.size > MAX_BYTES) return show('info', 'File terlalu besar (maks. 200 MB untuk pemeriksaan di browser).');
        if (!(window.crypto && crypto.subtle)) return show('info', 'Browser ini tidak mendukung hashing lokal. Tempel hash SHA-256 secara manual.');
        show('info', 'Menghitung sidik jari <strong>' + esc(file.name) + '</strong>…');
        sha256 = toHex(await crypto.subtle.digest('SHA-256', await file.arrayBuffer()));
        label = esc(file.name) + '<br>';
      } else if (!/^[a-f0-9]{64}$/.test(sha256)) {
        return show('info', 'Hash harus 64 karakter heksadesimal (SHA-256).');
      }

      var res = await fetch('/antivirus/check-hash', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ sha256: sha256, _csrf: btn.getAttribute('data-csrf') }),
      });
      if (res.status === 429) return show('info', 'Terlalu banyak pemeriksaan. Coba lagi dalam satu menit.');
      if (!res.ok) return show('info', 'Pemeriksaan gagal (HTTP ' + res.status + '). Muat ulang halaman lalu coba lagi.');
      var data = await res.json();
      var hashLine = '<div class="mono scan-hash">' + esc(data.sha256) + '</div>';
      if (data.malicious) {
        show('bad', '<strong>⚠️ Ancaman terdeteksi: ' + esc(data.threatName) + '</strong> (tingkat ' + esc(data.severity) + ')<br>' +
          label + 'Jangan buka atau pasang file ini. Hapus dari perangkat Anda.' + hashLine);
      } else {
        show('ok', '<strong>✓ Tidak ditemukan di database ancaman.</strong><br>' + label +
          '<span class="subtitle">Hasil ini hanya mencocokkan signature yang dikenal — tetap waspada dengan file dari sumber tak resmi.</span>' + hashLine);
      }
    } catch (e) {
      show('info', 'Terjadi kesalahan: ' + esc(e.message));
    } finally {
      btn.disabled = false;
    }
  });
})();
