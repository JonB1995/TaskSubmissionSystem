default:
	javac -cp libs/jersey-bundle-1.17.1.jar:libs/servlet-api.jar:libs/jersey-multipart-1.17.1.jar:libs/mimepull-1.6.jar:/usr/share/tomcat8/lib/servlet-api.jar Server.java ChordNode.java IChordNode.java
	sudo cp -r myapp /var/lib/tomcat8/webapps/
	sudo chown -R tomcat8 /var/lib/tomcat8/webapps/myapp/
	cp Server.class myapp/WEB-INF/classes
	cp ChordNode.class myapp/WEB-INF/classes
	cp IChordNode.class myapp/WEB-INF/classes
	cp Finger.class myapp/WEB-INF/classes
	cp Store.class myapp/WEB-INF/classes
