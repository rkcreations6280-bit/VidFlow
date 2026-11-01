package sysModules.browserEngine;

/**
 * JavaScript that is injected into every page. Written as plain text so it needs
 * no libraries. It never sends anything anywhere: it only talks to the app through
 * the (randomly named) bridge.
 */
public final class BrowserScripts {

	private BrowserScripts() {}

	public static String build(String bridge, boolean popupBlock, boolean touchBlock,
	                           boolean cosmetic, boolean youtubeAds, boolean playButton) {
		StringBuilder js = new StringBuilder(4096);
		js.append("(function(){\n")
			.append("if(window.__ngInit)return;window.__ngInit=true;\n")
			.append("var B=window['").append(bridge).append("'];\n")
			.append("function tell(k){try{B&&B.blocked(k);}catch(e){}}\n")
			.append("var lastGesture=0;\n")
			.append("['touchstart','mousedown','keydown','pointerdown'].forEach(function(t){")
			.append("document.addEventListener(t,function(e){if(e.isTrusted)lastGesture=Date.now();},true);});\n")
			.append("function gesture(){return Date.now()-lastGesture<1200;}\n")
			// Privacy signals + referrer trimming
			.append("try{Object.defineProperty(navigator,'globalPrivacyControl',{get:function(){return true;}});}catch(e){}\n")
			.append("try{Object.defineProperty(navigator,'doNotTrack',{get:function(){return '1';}});}catch(e){}\n")
			.append("try{var m=document.createElement('meta');m.name='referrer';")
			.append("m.content='strict-origin-when-cross-origin';(document.head||document.documentElement).appendChild(m);}catch(e){}\n");

		if (popupBlock || touchBlock) {
			// Tap tracker + click-through shield: peel invisible layers off the tap point so the tap
			// reaches the real element, and tell the app what the user really tapped.
			js.append("function vis(el){try{var cs=getComputedStyle(el);var op=parseFloat(cs.opacity);if(op<0.06)return false;")
				.append("if((el.innerText||'').trim().length>0)return true;var t=el.tagName;")
				.append("if(t==='IMG'||t==='SVG'||t==='svg'||t==='CANVAS'||t==='VIDEO'||t==='PICTURE'||t==='AUDIO')return true;")
				.append("if(cs.backgroundImage&&cs.backgroundImage!=='none')return true;")
				.append("var bg=cs.backgroundColor;return !(bg==='rgba(0, 0, 0, 0)'||bg==='transparent');}catch(e){return true;}}\n")
				.append("function ctl(el){var n=el;for(var i=0;n&&n.nodeType===1&&i<6;i++,n=n.parentNode){var t=n.tagName;")
				.append("if(t==='A'&&n.href)return 'a';")
				.append("if(t==='BUTTON'||t==='INPUT'||t==='SELECT'||t==='TEXTAREA'||t==='LABEL'||t==='SUMMARY'||t==='VIDEO'||t==='AUDIO')return 'ctl';")
				.append("var r=n.getAttribute&&n.getAttribute('role');if(r==='button'||r==='link'||r==='menuitem'||r==='tab')return 'ctl';}return '';}\n")
				.append("function bigClear(el){try{if(!el||el===document.body||el===document.documentElement)return false;")
				.append("var r=el.getBoundingClientRect();if(r.width<innerWidth*0.35||r.height<innerHeight*0.3)return false;")
				.append("if(ctl(el))return false;return !vis(el);}catch(e){return false;}}\n")
				.append("function peel(x,y){var el=null;for(var k=0;k<4;k++){el=document.elementFromPoint(x,y);")
				.append("if(!el||!bigClear(el))break;")
				.append("el.style.setProperty('pointer-events','none','important');tell('touch');}return el;}\n")
				.append("['pointerdown','touchstart','mousedown'].forEach(function(t){document.addEventListener(t,function(e){")
				.append("if(!e.isTrusted)return;try{var p=(e.touches&&e.touches[0])||e;var el=peel(p.clientX,p.clientY);")
				.append("var k=el?ctl(el):'';if(!k)k=(el&&vis(el))?'vis':'other';if(B&&B.tap)B.tap(k);}catch(x){}},true);});\n");
		}

		if (popupBlock) {
			js.append("var _open=window.open;\n")
				.append("window.open=function(){if(!gesture()){tell('popup');return null;}return _open.apply(this,arguments);};\n")
				.append("var _click=HTMLAnchorElement.prototype.click;\n")
				.append("HTMLAnchorElement.prototype.click=function(){")
				.append("if(!gesture()&&!this.download&&this.target&&this.target!=='_self'){tell('popup');return;}")
				.append("return _click.apply(this,arguments);};\n")
				.append("document.addEventListener('click',function(e){")
				.append("if(!e.isTrusted&&!gesture()){var a=e.target&&e.target.closest?e.target.closest('a'):null;")
				.append("if(a&&a.target&&a.target!=='_self'){e.preventDefault();e.stopImmediatePropagation();tell('popup');}}},true);\n");
		}

		if (touchBlock) {
			// Hidden touch blocker: invisible full-screen layers that steal taps to open ads.
			js.append("function sweepNode(el){\n")
				.append(" try{if(!el||el.nodeType!==1)return;var cs=getComputedStyle(el);\n")
				.append(" if(cs.position!=='fixed'&&cs.position!=='absolute')return;\n")
				.append(" var r=el.getBoundingClientRect();var w=window.innerWidth,h=window.innerHeight;\n")
				.append(" if(r.width<w*0.7||r.height<h*0.5)return;\n")
				.append(" var op=parseFloat(cs.opacity);var bg=cs.backgroundColor;\n")
				.append(" var clear=(bg==='rgba(0, 0, 0, 0)'||bg==='transparent');\n")
				.append(" var txt=(el.innerText||'').trim().length;\n")
				.append(" var media=el.querySelector&&el.querySelector('video,audio,canvas,img,input,button,form');\n")
				.append(" var z=parseInt(cs.zIndex);\n")
				.append(" if(op<0.06||(clear&&txt===0&&!media&&(isNaN(z)||z>=10))){\n")
				.append("  el.style.setProperty('pointer-events','none','important');\n")
				.append("  el.style.setProperty('display','none','important');tell('touch');}\n")
				.append(" }catch(e){}}\n")
				.append("function sweepAll(){try{var b=document.body;if(!b)return;")
				.append("var kids=b.children;for(var i=0;i<kids.length;i++)sweepNode(kids[i]);")
				.append("var d=b.querySelectorAll('div,a,span,iframe');")
				.append("for(var j=0;j<d.length&&j<400;j++)sweepNode(d[j]);}catch(e){}}\n")
				.append("var sweepTimer=0,sweepCount=0;\n")
				.append("function schedule(){if(sweepTimer||sweepCount>60)return;")
				.append("sweepTimer=setTimeout(function(){sweepTimer=0;sweepCount++;sweepAll();},700);}\n")
				.append("try{new MutationObserver(function(ms){")
				.append("for(var i=0;i<ms.length;i++){var n=ms[i].addedNodes;")
				.append("for(var j=0;j<n.length;j++)sweepNode(n[j]);}schedule();})")
				.append(".observe(document.documentElement,{childList:true,subtree:true});}catch(e){}\n")
				.append("document.addEventListener('DOMContentLoaded',sweepAll);\n")
				.append("window.addEventListener('load',function(){sweepAll();});\n");
		}

		if (cosmetic) {
			js.append("try{var st=document.createElement('style');st.textContent=")
				.append("'ins.adsbygoogle,.adsbygoogle,[id^=\"google_ads\"],[id^=\"div-gpt-ad\"],")
				.append("[class*=\"ad-banner\"],[class*=\"adbanner\"],[class*=\"sponsored-ad\"],")
				.append("iframe[src*=\"doubleclick\"],iframe[src*=\"googlesyndication\"],")
				.append("iframe[src*=\"adservice\"],a[href*=\"doubleclick.net\"],")
				.append("[data-ad-slot],[data-google-query-id],.ytd-ad-slot-renderer,")
				.append("ytd-display-ad-renderer,ytd-promoted-sparkles-web-renderer,")
				.append("ytm-promoted-sparkles-web-renderer,ytm-companion-ad-renderer,")
				.append("#masthead-ad,.ytp-ad-overlay-container{display:none!important}';")
				.append("(document.head||document.documentElement).appendChild(st);}catch(e){}\n");
		}

		if (youtubeAds) {
			js.append("if(/(^|\\.)youtube\\.com$/.test(location.hostname)){\n")
				.append(" var strip=function(o){try{if(o&&typeof o==='object'){")
				.append("delete o.adPlacements;delete o.playerAds;delete o.adSlots;")
				.append("delete o.adBreakHeartbeatParams;}}catch(e){}return o;};\n")
				.append(" var _parse=JSON.parse;JSON.parse=function(){return strip(_parse.apply(this,arguments));};\n")
				.append(" try{var _json=Response.prototype.json;Response.prototype.json=function(){")
				.append("return _json.apply(this,arguments).then(strip);};}catch(e){}\n")
				.append(" setInterval(function(){try{var p=document.querySelector('.html5-video-player,#movie_player');")
				.append("if(p&&p.classList.contains('ad-showing')){var v=document.querySelector('video');")
				.append("if(v&&isFinite(v.duration)){v.muted=true;v.currentTime=v.duration;}")
				.append("var s=document.querySelector('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button');")
				.append("if(s)s.click();}}catch(e){}},400);\n")
				.append("}\n");
		}

		// Media detection for the download button.
		js.append("var rt=0;\n")
			.append("function report(){rt=0;try{var m=document.querySelectorAll('video,audio');")
			.append("var playing=false,src='',kind='video';\n")
			.append("for(var i=0;i<m.length;i++){var e=m[i];")
			.append("if(!e.paused&&!e.ended&&e.readyState>2&&(e.duration>1||!isFinite(e.duration))){")
			.append("playing=true;src=e.currentSrc||e.src||'';kind=e.tagName==='AUDIO'?'audio':'video';break;}}\n")
			.append("B&&B.media(playing,src,kind);}catch(e){}}\n")
			.append("function soon(){if(!rt)rt=setTimeout(report,250);}\n")
			.append("['play','playing','pause','ended','emptied','loadeddata','waiting'].forEach(function(t){")
			.append("document.addEventListener(t,function(e){var n=e.target&&e.target.tagName;")
			.append("if(n==='VIDEO'||n==='AUDIO')soon();},true);});\n")
			.append("setInterval(report,2500);\n");

		if (playButton) {
			// Floating "play in the app's own player" button over the playing video.
			// Closed shadow DOM + trusted-click check: the page cannot see or press it.
			js.append("try{\n")
				.append("var host=document.createElement('div');host.setAttribute('data-ng','1');")
				.append("host.style.cssText='all:initial;position:fixed;top:0;left:0;width:0;height:0;z-index:2147483647;';\n")
				.append("var sh=host.attachShadow({mode:'closed'});\n")
				.append("var st=document.createElement('style');st.textContent=")
				.append("'.b{position:fixed;width:42px;height:42px;border-radius:50%;display:none;align-items:center;")
				.append("justify-content:center;background:linear-gradient(135deg,#FFC837,#F57C00);")
				.append("box-shadow:0 2px 10px rgba(0,0,0,.55);opacity:.92;touch-action:manipulation;}")
				.append(".b svg{width:22px;height:22px;fill:#2A1600;pointer-events:none;}';\n")
				.append("var btn=document.createElement('div');btn.className='b';")
				.append("btn.innerHTML='<svg viewBox=\"0 0 24 24\"><path d=\"M8 5v14l11-7z\"/></svg>';\n")
				.append("sh.appendChild(st);sh.appendChild(btn);\n")
				.append("var cur=null;\n")
				.append("function pick(){var vs=document.getElementsByTagName('video');var best=null,area=0;\n")
				.append(" for(var i=0;i<vs.length;i++){var v=vs[i];var r=v.getBoundingClientRect();\n")
				.append("  if(r.width<160||r.height<90)continue;\n")
				.append("  var vw=Math.min(r.right,window.innerWidth)-Math.max(r.left,0);\n")
				.append("  var vh=Math.min(r.bottom,window.innerHeight)-Math.max(r.top,0);\n")
				.append("  if(vw<r.width*0.4||vh<r.height*0.4)continue;\n")
				.append("  var active=!v.paused||(v.currentTime>0&&!v.ended);if(!active)continue;\n")
				.append("  if(vw*vh>area){area=vw*vh;best=v;}}\n")
				.append(" return best;}\n")
				.append("function place(){var v=pick();cur=v;\n")
				.append(" if(!v){btn.style.display='none';return;}\n")
				.append(" var r=v.getBoundingClientRect();\n")
				.append(" var x=Math.min(r.right,window.innerWidth)-42-10;\n")
				.append(" var y=Math.max(r.top,0)+10;\n")
				.append(" btn.style.left=Math.max(4,x)+'px';btn.style.top=y+'px';btn.style.display='flex';}\n")
				.append("var raf=0;function sched(){if(raf)return;raf=requestAnimationFrame(function(){raf=0;place();});}\n")
				.append("window.addEventListener('scroll',sched,true);window.addEventListener('resize',sched);\n")
				.append("['play','playing','pause','ended','loadeddata','emptied'].forEach(function(t){")
				.append("document.addEventListener(t,sched,true);});\n")
				.append("setInterval(sched,700);\n")
				.append("function attach(){var p=document.body||document.documentElement;if(p&&!host.parentNode)p.appendChild(host);}\n")
				.append("attach();document.addEventListener('DOMContentLoaded',attach);\n")
				.append("setInterval(attach,2000);\n")
				.append("btn.addEventListener('click',function(e){e.preventDefault();e.stopImmediatePropagation();")
				.append("if(!e.isTrusted||!cur)return;\n")
				.append(" var v=cur;var tr=[];var ts=v.querySelectorAll('track');\n")
				.append(" for(var i=0;i<ts.length;i++){var t=ts[i];if(t.src&&(t.kind==='subtitles'||t.kind==='captions'||!t.kind))")
				.append("tr.push({src:t.src,label:t.label||'',lang:t.srclang||''});}\n")
				.append(" var src=v.currentSrc||v.src||'';\n")
				.append(" try{v.pause();}catch(x){}\n")
				.append(" B&&B.playInApp(src,document.title||'',JSON.stringify(tr));},true);\n")
				.append("}catch(e){}\n");
		}
		js.append("})();");
		return js.toString();
	}
}
