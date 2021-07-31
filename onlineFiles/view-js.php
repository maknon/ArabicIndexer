<?php

/*
This script is not used any more since google cannot extract links from it. otherwise. it is very optimized since only one file is loaded to the client and only fetch images when moving between pages using JS
*/

if (empty($_GET['bk'])) {
	echo "Book id is missing";
	return;
}
else
	$bk = $_GET['bk'];

if (empty($_GET['t']))
	$title = 'مكتبة مكنون المصورة';
else
	$title = $_GET['t'];

$imagesDirectory = "bk/".$bk;
$baseDirectory = "https://www.maknoon.com/ai/bk/".$bk;

$fi = new FilesystemIterator($imagesDirectory, FilesystemIterator::SKIP_DOTS);
$pageMax = (iterator_count($fi)-1)/2;

if (empty($_GET['p']))
	$currentPage = 1;
else {
	$p = $_GET['p'];
	// TODO: No need for any check. Remove
	if(is_numeric($p)) {
		if($p >= 1 && $p <= $pageMax)
			$currentPage = $p;
		else
			$currentPage = 1;
	} else
		$currentPage = 1;
}

echo "<!DOCTYPE html>
<html>
<head>
<meta name='viewport' content='width=device-width, initial-scale=1'>
<title>$title</title>
<style>
body {
	margin: 0;
}

.navbar {
	overflow: hidden;
	background-color: transparent;
	position: fixed;
	top: 0px;
	left: 0px;
	border: 1px solid #ccc;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
}

.navbar a {
	float: left;
	display: block;
	padding: 10px 10px;
}

.navbar a:hover {
	background: rgba(221, 221, 221, 0.3);
}

.center {
	display: block;
	max-width: 100%;
	margin-left: auto;
	margin-right: auto;
}
</style>
</head>
<body>

<div class='navbar' id='navbar'>
	<div>
		<a onclick='swap()'>
			<img src='swap.svg' id='swap' alt='Collapse'>
		</a>
	</div>
	<div id='home'>
		<a href='https://www.maknoon.com/community/pages/ai/'>
			<img src='home.svg' alt='Back to Main'>
		</a>
	</div>
	<div id='last'>
		<a onclick='lastPage()'>
			<img src='last.svg' alt='Last Page'>
		</a>
	</div>
	<div id='next'>
		<a onclick='nextPage()'>
			<img src='forward.svg' alt='Next Page'>
		</a>
	</div>
	<div id='pageInput'>
		<input value='$currentPage' id='inp' type='text' autocomplete='off' onkeydown='goTo(this)' style='padding:5px;font-size:15px;font-family:inherit;text-align:center;outline:none;background:transparent;border:1px solid #ccc;width:2rem;'>
	</div>
	<div id='maxP'>
		<label style='background:rgb(204,204,204,0.3);padding:5px;font-size:15px;display:inline-block;text-align:center;border:1px solid #ccc;width:2rem;'>$pageMax</label>
	</div>
	<div id='back'>
		<a onclick='previousPage()'>
			<img src='back.svg' alt='Previous Page'>
		</a>
	</div>
	<div id='first'>
		<a onclick='firstPage()'>
			<img src='first.svg' alt='First Page'>
		</a>
	</div>
	<div id='pdf'>
		<a href='$baseDirectory/$bk.pdf'>
			<img src='pdf.svg' alt='Download as PDF'>
		</a>
	</div>
	<div id='text'>
		<a onclick='showText()'>
			<img src='text.svg' alt='Show as text'>
		</a>
	</div>
</div>

<img src='$imagesDirectory/$currentPage.png' id='page' class='center'>
";

echo "
<script type='text/javascript'>
	var currentPage = $currentPage;
	var pageMax = $pageMax;

	function firstPage() {
		currentPage = 1;
		document.getElementById('page').src = `$baseDirectory/1.png`;
		document.getElementById('inp').value = `\${currentPage}`;
		window.history.pushState({}, '$title', `/ai/view.php?bk=$bk&p=\${currentPage}`);
	}
	
	function lastPage() {
		currentPage = pageMax;
		document.getElementById('page').src = `$baseDirectory/\${currentPage}.png`;
		document.getElementById('inp').value = `\${currentPage}`;
		window.history.pushState({}, '$title', `/ai/view.php?bk=$bk&p=\${currentPage}`);
	}
	
	function nextPage() {
		if((currentPage + 1) <= pageMax) {
			currentPage = currentPage + 1;
			document.getElementById('page').src = `$baseDirectory/\${currentPage}.png`;
			document.getElementById('inp').value = `\${currentPage}`;
			window.history.pushState({}, '$title', `/ai/view.php?bk=$bk&p=\${currentPage}`);
		}
	}
	
	function previousPage() {
		if((currentPage - 1) > 0) {
			currentPage = currentPage - 1;
			document.getElementById('page').src = `$baseDirectory/\${currentPage}.png`;
			document.getElementById('inp').value = `\${currentPage}`;
			window.history.pushState({}, '$title', `/ai/view.php?bk=$bk&p=\${currentPage}`);
		}
	}
	
	function goTo(e) {
		if(event.keyCode == 13) {
			var page = Number(e.value);
			if(isNaN(page)) {
				document.getElementById('inp').value = `\${currentPage}`;
			} else {
				if(page >= 1 && page <= pageMax) {
					currentPage = page;
					document.getElementById('page').src = `$baseDirectory/\${currentPage}.png`;
					window.history.pushState({}, '$title', `/ai/view.php?bk=$bk&p=\${currentPage}`);
				} else {
					document.getElementById('inp').value = `\${currentPage}`;
				}
			}
		}
	}
	
	function showText() {
		pwin = window.open(`$baseDirectory/\${currentPage}.html`,'_blank');
	}
	
	function swap() {
		if(document.getElementById('swap').alt == 'Collapse') {
			document.getElementById('home').style.display = 'none';
			document.getElementById('first').style.display = 'none';
			document.getElementById('next').style.display = 'none';
			document.getElementById('pageInput').style.display = 'none';
			document.getElementById('maxP').style.display = 'none';
			document.getElementById('last').style.display = 'none';
			document.getElementById('back').style.display = 'none';
			document.getElementById('pdf').style.display = 'none';
			document.getElementById('text').style.display = 'none';
			document.getElementById('swap').alt = 'Expand';
		} else {
			document.getElementById('home').style.display = 'block';
			document.getElementById('first').style.display = 'block';
			document.getElementById('next').style.display = 'block';
			document.getElementById('pageInput').style.display = 'block';
			document.getElementById('maxP').style.display = 'block';
			document.getElementById('last').style.display = 'block';
			document.getElementById('back').style.display = 'block';
			document.getElementById('pdf').style.display = 'block';
			document.getElementById('text').style.display = 'block';
			document.getElementById('swap').alt = 'Collapse';
		}
	}

</script>

</body>
</html>
";
?>
