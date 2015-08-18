	- Each line in the reference dataset file should contain one record:
		<uri>	<ref doc string>
	- To build indexes for the reference dataset, run the command:
		python script.py INDEX_FOLDER REFERENCE_FILE
	- The input file should be formatted like the reference file:
		<uri>	<query string>
	- To run the program on the input file run the command:
		python script.py INDEX_FOLDER INPUT_FILE 1
	- The output will be written in output.txt
	