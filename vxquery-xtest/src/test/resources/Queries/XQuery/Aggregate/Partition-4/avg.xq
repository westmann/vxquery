(: XQuery Aggregate Query :)
(: Find the average minimum temperature.                                            :)
fn:avg(
    let $collection := "ghcnd_quarter_1|ghcnd_quarter_2|ghcnd_quarter_3|ghcnd_quarter_4"
    for $r in collection($collection)/dataCollection/data
    where $r/dataType eq "TMIN" 
    return $r/value
)