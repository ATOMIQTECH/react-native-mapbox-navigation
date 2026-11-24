#!/usr/bin/env perl
use strict; use warnings;

my ($path, $is_guard, $is_config) = @ARGV;
$path      //= '?';
$is_guard  //= 0;
$is_config //= 0;

my $raw = do { local $/; <STDIN> };
exit 0 unless defined $raw && length $raw;

my $MAX_LINE       = 1000;
my $MAX_RUN_SPACES = 100;
my $MAX_HEX        = 25;
my $MAX_ESC        = 50;
my $MAX_B64        = 512;

my @found;
sub block { push @found, "BLOCK|$_[0]" }
sub warm  { push @found, "WARN|$_[0]"  }

my $norm = $raw;
$norm =~ s/\\x([0-9a-fA-F]{2})/chr(hex($1))/ge;
$norm =~ s/\\u\{?([0-9a-fA-F]{4})\}?/chr(hex($1))/ge;
$norm = lc $norm;
$norm =~ s/['"`,+\s]//g;
$norm =~ s/\\//g;

sub has_svg_path_data {
  my ($s) = @_;
  while ($s =~ /(['"])([MmZz][^'"]{80,}?)\1/gs) {
    my $d = $2;
    my $ok = () = ($d =~ /[0-9.,\-\s MmLlHhVvCcSsQqTtAaZz]/g);
    return 1 if $ok >= 0.97 * length($d);
  }
  return 0;
}
my $is_asset = ( $raw =~ m{data:(?:image|font)/}i
              || $raw =~ /<svg|<path|viewbox/i
              || $raw =~ /\bd=["'{]\s*[Mm][\s0-9.,-]/
              || has_svg_path_data($raw) ) ? 1 : 0;

my $obfuscated = 0;

my $longest = 0;
for my $l (split /\n/, $raw) { $longest = length($l) if length($l) > $longest }
if ($longest > $MAX_LINE) {
  if ($is_asset) { warm("$path — line of $longest chars, but the file holds inline SVG/data URIs") }
  else           { block("$path — line of $longest chars (limit $MAX_LINE); padded-payload shape"); $obfuscated = 1 }
}

if ($raw =~ /([ \t]{$MAX_RUN_SPACES,})/) {
  block("$path — run of " . length($1) . " whitespace chars; off-screen padding shape");
  $obfuscated = 1;
}

my $hex = () = ($raw =~ /_0x[0-9a-fA-F]{4,}/g);
if ($hex >= $MAX_HEX) { block("$path — $hex hex-mangled identifiers; obfuscator output"); $obfuscated = 1 }

my $esc = () = ($raw =~ /\\x[0-9a-fA-F]{2}/g);
if ($esc >= $MAX_ESC) { block("$path — $esc \\xNN escapes; obfuscator output"); $obfuscated = 1 }

if (!$is_asset) {
  my $b64max = 0;
  while ($raw =~ m{([A-Za-z0-9+/]{$MAX_B64,}={0,2})}g) {
    $b64max = length($1) if length($1) > $b64max;
  }
  if ($b64max >= $MAX_B64) { block("$path — inline base64 blob of $b64max chars"); $obfuscated = 1 }
}

if (!$is_guard && $norm =~ /\[push\]\([^)]{0,40}\[shift\]\(\)\)/) {
  block("$path — string-array rotation preamble (push/shift); obfuscator output");
  $obfuscated = 1;
}

my @HIGH = qw(
  eth_blocknumber eth_gettransactioncount eth_getblockbynumber
  ethereum-rpc.publicnode.com eth.drpc.org public.blastapi.io
  eth.blockscout.com 1rpc.io/eth
  0xa322e5f3d311d3080e6f0121063e9adc2490ef1a
  x-payload-b64 missingx-payload-b6 emptypayloadbody
  :443/0x/ls :443/0x/cl module=account&action=txlist
  global[_v]= global[_h]= global[_h2]= global[_t_s]= global[_t_u]=
  global[r]=require global[m]=module
);
push @HIGH, 'q4fzkxx{!h', 'y-p_>d$0b&', '@^1aqk';
my @CTX = ('node:child_process', 'windowshide', 'detached:!![]', 'fromcharcode');

if (!$is_guard) {
  my @h = grep { index($norm, $_) >= 0 } @HIGH;
  block("$path — known payload indicators: @h") if @h;

  my @c = grep { index($norm, $_) >= 0 } @CTX;
  if (@c) {
    if ($obfuscated) { block("$path — obfuscated AND using loader APIs: @c") }
    else             { warm("$path — uses loader-adjacent APIs (normal in tooling): @c") }
  }
}

if ($is_config) {
  my @ex;
  for my $api ('child_process', 'eval(', 'newfunction(', 'spawnsync',
               'spawn(', 'atob(', 'frombase64') {
    push @ex, $api if index($norm, $api) >= 0;
  }
  block("$path — config file contains runtime/exec APIs: @ex") if @ex;
  warm("$path — config file uses createRequire; confirm it is yours")
    if index($norm, 'createrequire') >= 0;
  block("$path — config file passes eval as a value (indirect eval)")
    if $norm =~ /\(eval,|\[eval\]|=eval;/;
}

if ($path eq 'package.json' && $raw =~ /"scripts"\s*:\s*\{(.*?)\}/s) {
  my $s = $1;
  while ($s =~ /"(preinstall|install|postinstall|prepare|prepublish|prepublishOnly)"\s*:\s*"((?:[^"\\]|\\.)*)"/g) {
    my ($hook, $cmd) = ($1, $2);
    if ($cmd =~ /node\s+-e|curl|wget|base64|eval|child_process|\|\s*(?:sh|bash)|chmod/i) {
      block("package.json — lifecycle script runs fetched or evaluated code: $hook");
    } else {
      warm("package.json — lifecycle script present, confirm you added it: $hook");
    }
  }
}

if ($path =~ m{^\.github/workflows/}
    && $raw =~ /(?:curl|wget)[^|\n]*\|\s*(?:sudo\s+)?(?:sh|bash)/) {
  block("$path — workflow pipes a download straight into a shell");
}

print "$_\n" for @found;
exit 0;
