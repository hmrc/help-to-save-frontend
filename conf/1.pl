use strict;
use File::Slurper 'read_text';
use Text::CSV qw( csv );
use Data::Dumper;



# Read whole file in memory
#my $aoa = csv (in => "messages", encoding => "UTF-8");    # as array of array

my $content = read_text("messages", "UTF-8");
my @aoa;

for my $line (split("\n", $content)) {
 my @a = split("=", $line, 2);
# print ("---\n");
# print Dumper(@a);
# print ("\n");
# my $s = @a;
# print $s;
# @a = (1,2);
# print Dumper(scalar @a);
 if ((scalar @a) > 1) {
   push @aoa, [@a];
 }
}

print Dumper(@aoa);


# Write array of arrays as csv file
#csv (in => \@aoa, out => "messages.csv", sep_char=> ",", encoding => "UTF-8");
