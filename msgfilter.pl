# msgfilter.pl - remove Co-Authored-By: Warp <agent@warp.dev> lines from commit messages
use strict;
use warnings;
while (my $line = <STDIN>) {
  if ($line =~ /^Co-Authored-By:\s*Warp\s*<agent\@warp\.dev>\s*$/i) {
    next;
  }
  print $line;
}
