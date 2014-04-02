(: XQuery Join Query :)
(: Find the highest recorded temperature (TMAX) for each station for each     :)
(: day over the year 2002.                                                    :)
let $station_collection := "/Users/prestoncarman/Documents/smartsvn/vxquery_git_master/vxquery-xtest/tests/TestSources/ghcnd/half_1/quarter_1/|/Users/prestoncarman/Documents/smartsvn/vxquery_git_master/vxquery-xtest/tests/TestSources/ghcnd/half_1/quarter_2/|/Users/prestoncarman/Documents/smartsvn/vxquery_git_master/vxquery-xtest/tests/TestSources/ghcnd/half_2/quarter_3/|/Users/prestoncarman/Documents/smartsvn/vxquery_git_master/vxquery-xtest/tests/TestSources/ghcnd/half_2/quarter_4/"
for $s in collection($station_collection)/stationCollection/station

let $sensor_collection := "/Users/prestoncarman/Documents/smartsvn/vxquery_git_master/vxquery-xtest/tests/TestSources/ghcnd/half_1/quarter_1/|/Users/prestoncarman/Documents/smartsvn/vxquery_git_master/vxquery-xtest/tests/TestSources/ghcnd/half_1/quarter_2/|/Users/prestoncarman/Documents/smartsvn/vxquery_git_master/vxquery-xtest/tests/TestSources/ghcnd/half_2/quarter_3/|/Users/prestoncarman/Documents/smartsvn/vxquery_git_master/vxquery-xtest/tests/TestSources/ghcnd/half_2/quarter_4/"
for $r in collection($sensor_collection)/dataCollection/data

where $s/id eq $r/station
    and $r/dataType eq "TMAX" 
    and fn:year-from-dateTime(xs:dateTime(fn:data($r/date))) eq 2002
return ($s/displayName, $r/date, $r/value)