# msgfilter.pl -lines from commit messages
use strict;
use warnings;
while (my $line = <STDIN>) {
  if ($line =~ /^Co-Authored-By:\s*Warp\s*<agent\@warp\.dev>\s*$/i) {
    next;
  }
  print $line;
}

