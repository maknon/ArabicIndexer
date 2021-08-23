<?php

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

$nextPageClass = 'n';
$previousPageClass = 'n';
$lastPageClass = 'n';
$firstPageClass = 'n';
	
if (($currentPage + 1) <= $pageMax)
	$nextPage = $currentPage + 1;
else {
	$nextPage = $currentPage;
	$nextPageClass = 'disabled';
	$lastPageClass = 'disabled';
}

if (($currentPage - 1) > 0)
	$previousPage = $currentPage - 1;
else {
	$previousPage = $currentPage;
	$previousPageClass = 'disabled';
	$firstPageClass = 'disabled';
}

echo "<!DOCTYPE html>
<html>
<head>
<meta name='viewport' content='width=device-width, initial-scale=1'>
<title>$title</title>
<link rel='icon' href='favicon.png'>
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

.img {
	width:100%;
	margin: auto;
}

.img-container {
	position: absolute;
	top: 0;
	bottom: 0;
	left: 0;
	right: 0;
	display: flex;
}

.disabled {
    pointer-events: none;
    opacity: 0.2;
}

</style>
</head>
<body>

<div class='img-container'>
	<img src='$imagesDirectory/$currentPage.png' id='page' class='img'>
</div>

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
	<div id='last' class='$lastPageClass'>
		<a href='https://www.maknoon.com/ai/view.php?bk=$bk&amp;p=$pageMax&amp;t=$title'>
			<img src='last.svg' alt='Last Page'>
		</a>
	</div>
	<div id='next' class='$nextPageClass'>
		<a href='https://www.maknoon.com/ai/view.php?bk=$bk&amp;p=$nextPage&amp;t=$title'>
			<img src='forward.svg' alt='Next Page'>
		</a>
	</div>
	<div id='pageInput'>
		<input value='$currentPage' id='inp' type='text' autocomplete='off' onkeydown='goTo(this)' style='padding:5px;font-size:15px;font-family:Arial;text-align:center;outline:none;background:transparent;border:1px solid;border-color:rgb(200,200,200,0.5);width:2rem;'>
	</div>
	<div id='maxP'>
		<label style='background:rgb(200,200,200,0.5);padding:5px;font-size:15px;font-family:Arial;display:inline-block;text-align:center;border:1px solid;border-color:rgb(200,200,200,0.5);width:2rem;'>$pageMax</label>
	</div>
	<div id='back' class='$previousPageClass'>
		<a href='https://www.maknoon.com/ai/view.php?bk=$bk&amp;p=$previousPage&amp;t=$title'>
			<img src='back.svg' alt='Previous Page'>
		</a>
	</div>
	<div id='first' class='$firstPageClass'>
		<a href='https://www.maknoon.com/ai/view.php?bk=$bk&amp;p=1&amp;t=$title'>
			<img src='first.svg' alt='First Page'>
		</a>
	</div>
	<div id='pdf'>
		<a href='$baseDirectory/$bk.pdf'>
			<img src='pdf.svg' alt='Download as PDF'>
		</a>
	</div>
	<div id='text'>
		<a href='$baseDirectory/$currentPage.html' target='_blank'>
			<img src='text.svg' alt='Show as text'>
		</a>
	</div>
</div>
";

echo "
<script type='text/javascript'>
	var currentPage = $currentPage;
	var pageMax = $pageMax;

	function goTo(e) {
		if(event.keyCode == 13) {
			var page = Number(e.value);
			if(isNaN(page)) {
				window.location.href = 'https://www.maknoon.com/ai/view.php?bk=$bk&p=$currentPage';
			} else {
				if(page >= 1 && page <= pageMax) {
					window.location.href = `https://www.maknoon.com/ai/view.php?bk=$bk&p=\${page}`;
				} else {
					window.location.href = 'https://www.maknoon.com/ai/view.php?bk=$bk&p=$currentPage';
				}
			}
		}
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
